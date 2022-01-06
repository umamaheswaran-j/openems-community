package io.openems.edge.bridge.modbus.api.element;

import java.nio.ByteBuffer;

import io.openems.common.types.OpenemsType;
import io.openems.edge.common.type.TypeUtils;

/**
 * An SignedWordElement represents a Short value in an
 * {@link AbstractWordElement}.
 */
public class SignedWordElement extends AbstractWordElement<SignedWordElement, Short> {

	public SignedWordElement(int address) {
		super(OpenemsType.SHORT, address);
	}

	@Override
	protected SignedWordElement self() {
		return this;
	}

	protected Short fromByteBuffer(ByteBuffer buff) {
		return buff.order(getByteOrder()).getShort(0);
	}

	protected ByteBuffer toByteBuffer(ByteBuffer buff, Object object) {
		Short value = TypeUtils.getAsType(OpenemsType.SHORT, object);
		return buff.putShort(value.shortValue());
	}

}
