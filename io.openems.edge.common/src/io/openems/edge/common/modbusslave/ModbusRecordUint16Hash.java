package io.openems.edge.common.modbusslave;

public class ModbusRecordUint16Hash extends ModbusRecordUint16 {

	private final String text;

	public ModbusRecordUint16Hash(int offset, String text) {
		super(offset, "Hash of \"" + text + "\"", (short) text.hashCode());
		this.text = text;
	}

	@Override
	public String toString() {
		return "ModbusRecordUint16Hash [text=" + this.text + ", value=" + this.value + "/0x"
				+ Integer.toHexString(this.value) + ", type=" + getType() + "]";
	}

	@Override
	public String getValueDescription() {
		return "0x" + Integer.toHexString(this.value & 0xffff);
	}

}
