package io.openems.edge.bridge.modbus.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Multimap;

import io.openems.common.exceptions.OpenemsException;
import io.openems.common.worker.AbstractImmediateWorker;
import io.openems.edge.bridge.modbus.api.element.ModbusElement;
import io.openems.edge.bridge.modbus.api.task.ReadTask;
import io.openems.edge.bridge.modbus.api.task.Task;
import io.openems.edge.bridge.modbus.api.task.WaitTask;
import io.openems.edge.bridge.modbus.api.task.WriteTask;
import io.openems.edge.common.taskmanager.MetaTasksManager;
import io.openems.edge.common.taskmanager.Priority;

/**
 * The ModbusWorker schedules the execution of all Modbus-Tasks, like reading
 * and writing modbus registers.
 * 
 * <p>
 * It tries to execute all Write-Tasks as early as possible (directly after the
 * TOPIC_CYCLE_EXECUTE_WRITE event) and all Read-Tasks as late as possible to
 * have correct values available exactly when they are needed (i.e. at the
 * TOPIC_CYCLE_BEFORE_PROCESS_IMAGE event).
 */
public class ModbusWorker extends AbstractImmediateWorker {

	private static final long TASK_DURATION_BUFFER = 50;

	private final Logger log = LoggerFactory.getLogger(ModbusWorker.class);
	private final Stopwatch stopwatch = Stopwatch.createUnstarted();
	private final LinkedBlockingDeque<Task> tasksQueue = new LinkedBlockingDeque<>();
	private final MetaTasksManager<ReadTask> readTasksManager = new MetaTasksManager<>();
	private final MetaTasksManager<WriteTask> writeTasksManager = new MetaTasksManager<>();
	private final AbstractModbusBridge parent;

	// The measured duration between BeforeProcessImage event and ExecuteWrite event
	private long durationBetweenBeforeProcessImageTillExecuteWrite = 0;

	protected ModbusWorker(AbstractModbusBridge parent) {
		this.parent = parent;
	}

	/**
	 * This is called on TOPIC_CYCLE_BEFORE_PROCESS_IMAGE cycle event.
	 */
	protected void onBeforeProcessImage() {
		int cycleTime = this.parent.getCycle().getCycleTime();
		this.stopwatch.reset();
		this.stopwatch.start();

		// If the current tasks queue spans multiple cycles and we are in-between ->
		// stop here
		if (!this.tasksQueue.isEmpty()) {
			return;
		}

		// Collect the next read-tasks
		List<ReadTask> nextReadTasks = new ArrayList<>();
		ReadTask lowPriorityTask = this.getOneLowPriorityReadTask();
		if (lowPriorityTask != null) {
			nextReadTasks.add(lowPriorityTask);
		}
		nextReadTasks.addAll(this.getAllHighPriorityReadTasks());
		long readTasksDuration = 0;
		for (ReadTask task : nextReadTasks) {
			readTasksDuration += task.getExecuteDuration();
		}

		// collect the next write-tasks
		long writeTasksDuration = 0;
		List<WriteTask> nextWriteTasks = this.getAllWriteTasks();
		for (WriteTask task : nextWriteTasks) {
			writeTasksDuration += task.getExecuteDuration();
		}

		// plan the execution for the next cycles
		long totalDuration = readTasksDuration + writeTasksDuration;
		long totalDurationWithBuffer = totalDuration + TASK_DURATION_BUFFER;
		long noOfRequiredCycles = ceilDiv(totalDurationWithBuffer, cycleTime);

		// Set EXECUTION_DURATION channel
		this.parent._setExecutionDuration(totalDuration);

		// Set CYCLE_TIME_IS_TOO_SHORT state-channel
		if (noOfRequiredCycles > 1) {
			this.parent._setCycleTimeIsTooShort(true);
		} else {
			this.parent._setCycleTimeIsTooShort(false);
		}

		long durationOfTasksBeforeExecuteWriteEvent = 0;
		int noOfTasksBeforeExecuteWriteEvent = 0;
		for (ReadTask task : nextReadTasks) {
			if (durationOfTasksBeforeExecuteWriteEvent > this.durationBetweenBeforeProcessImageTillExecuteWrite) {
				break;
			}
			noOfTasksBeforeExecuteWriteEvent++;
			durationOfTasksBeforeExecuteWriteEvent += task.getExecuteDuration();
		}

		// Build Queue
		Deque<Task> tasksQueue = new LinkedList<>();

		// Add all write-tasks to the queue
		tasksQueue.addAll(nextWriteTasks);

		// Add all read-tasks to the queue
		for (int i = 0; i < nextReadTasks.size(); i++) {
			ReadTask task = nextReadTasks.get(i);
			if (i < noOfTasksBeforeExecuteWriteEvent) {
				// this Task will be executed before ExecuteWrite event -> add it to the end of
				// the queue
				tasksQueue.addLast(task);
			} else {
				// this Task will be executed after ExecuteWrite event -> add it to the
				// beginning of the queue
				tasksQueue.addFirst(task);
			}
		}

		// Add a waiting-task to the end of the queue
		long waitTillStart = noOfRequiredCycles * cycleTime - totalDurationWithBuffer;
		tasksQueue.addLast(new WaitTask(waitTillStart));

		// Copy all Tasks to the global tasks-queue
		this.tasksQueue.clear();
		this.tasksQueue.addAll(tasksQueue);
	}

	/**
	 * This is called on TOPIC_CYCLE_EXECUTE_WRITE cycle event.
	 */
	public void onExecuteWrite() {
		// calculate the duration between BeforeProcessImage event and ExecuteWrite
		// event. This duration is used for planning the queue in onBeforeProcessImage()
		if (this.stopwatch.isRunning()) {
			this.durationBetweenBeforeProcessImageTillExecuteWrite = this.stopwatch.elapsed(TimeUnit.MILLISECONDS);
		} else {
			this.durationBetweenBeforeProcessImageTillExecuteWrite = 0;
		}
	}

	@Override
	protected void forever() throws InterruptedException {
		Task task = this.tasksQueue.takeLast();

		// If there are no tasks in the bridge, there will always be only one
		// 'WaitTask'.
		if (task instanceof WaitTask && !this.hasTasks()) {
			return;
		}

		ModbusComponent modbusComponent = task.getParent();
		try {
			// execute the task
			int noOfExecutedSubTasks = task.execute(this.parent);

			if (noOfExecutedSubTasks > 0) {
				// no exception & at least one sub-task executed -> remove this component from
				// erroneous list and set the CommunicationFailedChannel to false
				if (modbusComponent != null) {
					modbusComponent._setModbusCommunicationFailed(false);
				}
			}

		} catch (OpenemsException e) {
			this.parent.logWarn(this.log, task.toString() + " execution failed: " + e.getMessage());

			// mark this component as erroneous
			if (modbusComponent != null) {
				modbusComponent._setModbusCommunicationFailed(true);
			}

			// invalidate elements of this task
			for (ModbusElement<?> element : task.getElements()) {
				element.invalidate(this.parent);
			}
		}
	}

	/**
	 * Gets one Read-Tasks with priority Low or Once.
	 * 
	 * @return a list of ReadTasks by Source-ID
	 */
	private ReadTask getOneLowPriorityReadTask() {
		// Get next Priority ONCE task
		ReadTask oncePriorityTask = this.readTasksManager.getOneTask(Priority.ONCE);
		if (oncePriorityTask != null && !oncePriorityTask.hasBeenExecuted()) {
			return oncePriorityTask;

		} else {
			// No more Priority ONCE tasks available -> add Priority LOW task
			ReadTask lowPriorityTask = this.readTasksManager.getOneTask(Priority.LOW);
			if (lowPriorityTask != null) {
				return lowPriorityTask;
			}
		}
		return null;
	}

	/**
	 * Gets all the High-Priority Read-Tasks.
	 * 
	 * <p>
	 * This checks if a device is listed as defective and - if it is - adds only one
	 * ReadTask of this Source-Component to the queue
	 * 
	 * @return a list of ReadTasks
	 */
	private List<ReadTask> getAllHighPriorityReadTasks() {
		Multimap<String, ReadTask> tasks = this.readTasksManager.getAllTasksBySourceId(Priority.HIGH);
		return this.filterDefectiveComponents(tasks);
	}

	/**
	 * Gets the Write-Tasks by Source-ID.
	 * 
	 * <p>
	 * This checks if a device is listed as defective and - if it is - adds only one
	 * WriteTask of this Source-Component to the queue
	 * 
	 * @return a list of WriteTasks by Source-ID
	 */
	private List<WriteTask> getAllWriteTasks() {
		Multimap<String, WriteTask> tasks = this.writeTasksManager.getAllTasksBySourceId();
		return this.filterDefectiveComponents(tasks);
	}

	/**
	 * Does this {@link ModbusWorker} have any Tasks?.
	 * 
	 * @return true if there are Tasks
	 */
	private boolean hasTasks() {
		return this.writeTasksManager.hasTasks() && this.readTasksManager.hasTasks();
	}

	/**
	 * Filters a Multimap with Tasks by Component-ID. For Components that are known
	 * to be defective, only one task is added; otherwise all tasks are added to the
	 * result. The idea is to not execute tasks that are known to fail.
	 * 
	 * @param <T>   the Task type
	 * @param tasks Tasks by Component-ID
	 * @return a list of filtered tasks
	 */
	private <T extends Task> List<T> filterDefectiveComponents(Multimap<String, T> tasks) {
		List<T> result = new ArrayList<>();
		for (Collection<T> tasksOfComponent : tasks.asMap().values()) {
			Iterator<T> iterator = tasksOfComponent.iterator();
			if (iterator.hasNext()) {
				T task = iterator.next(); // get first task
				ModbusComponent modbusComponent = task.getParent();
				if (modbusComponent.getModbusCommunicationFailed().get() == Boolean.TRUE) {
					// Component is known to be erroneous -> add only one Task
					result.add(task);
				} else {
					// Component is ok. All all tasks.
					result.addAll(tasksOfComponent);
				}
			}
		}
		return result;
	}

	/**
	 * Adds the protocol.
	 * 
	 * @param sourceId Component-ID of the source
	 * @param protocol the ModbusProtocol
	 */
	public void addProtocol(String sourceId, ModbusProtocol protocol) {
		this.readTasksManager.addTasksManager(sourceId, protocol.getReadTasksManager());
		this.writeTasksManager.addTasksManager(sourceId, protocol.getWriteTasksManager());
	}

	/**
	 * Removes the protocol.
	 * 
	 * @param sourceId Component-ID of the source
	 */
	public void removeProtocol(String sourceId) {
		this.readTasksManager.removeTasksManager(sourceId);
		this.writeTasksManager.removeTasksManager(sourceId);
	}

	/**
	 * This is a helper function. It calculates the opposite of Math.floorDiv().
	 * 
	 * <p>
	 * Source:
	 * https://stackoverflow.com/questions/27643616/ceil-conterpart-for-math-floordiv-in-java
	 * 
	 * @param x the dividend
	 * @param y the divisor
	 * @return the result of the division, rounded up
	 */
	private static long ceilDiv(long x, long y) {
		return -Math.floorDiv(-x, y);
	}
}