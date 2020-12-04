package com.diozero.devices.sandpit;

/*-
 * #%L
 * Organisation: diozero
 * Project:      Device I/O Zero - Core
 * Filename:     Ads112C04.java  
 * 
 * This file is part of the diozero project. More information about this project
 * can be found at http://www.diozero.com/
 * %%
 * Copyright (C) 2016 - 2020 diozero
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.tinylog.Logger;

import com.diozero.api.I2CDevice;
import com.diozero.util.Crc;
import com.diozero.util.SleepUtil;

public class Ads112C04 {
	/**
	 * The ADS112C04 has two address pins: A0 and A1. Each address pin can be tied
	 * to either DGND, DVDD, SDA, or SCL, providing 16 possible unique addresses.
	 * This configuration allows up to 16 different ADS112C04 devices to be present
	 * on the same I2C bus. Name format is A1_A0
	 */
	public static enum Address {
		GND_GND(0b01000000), GND_VDD(0b01000001), GND_SDA(0b01000010), GND_SCL(0b01000011), //
		VDD_GND(0b01000100), VDD_VDD(0b01000101), VDD_SDA(0b01000110), VDD_SCL(0b01000111), //
		SDA_GND(0b01001000), SDA_VDD(0b01001001), SDA_SDA(0b01001010), SDA_SCL(0b01001011), //
		SCL_GND(0b01001100), SCL_VDD(0b01001101), SCL_SDA(0b01001110), SCL_SCL(0b01001111);

		private int value;

		private Address(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}
	}

	public static enum GainConfig {
		GAIN_1(1, 0b000), GAIN_2(2, 0b001), GAIN_4(4, 0b010);

		private int gain;
		private byte mask;

		private GainConfig(int gain, int mask) {
			this.gain = gain;
			this.mask = (byte) (mask << C0_GAIN_BIT_START);
		}

		public int getGain() {
			return gain;
		}

		byte getMask() {
			return mask;
		}
	}

	public static enum PgaBypass {
		ENABLED(0), DISABLED(1);

		private byte mask;

		private PgaBypass(int mask) {
			this.mask = (byte) (mask << C0_PGA_BYPASS_BIT_START);
		}

		byte getMask() {
			return mask;
		}
	}

	public static enum DataRate {
		DR_20HZ(20, 0b000), DR_45HZ(45, 0b001), DR_90HZ(90, 0b010), DR_175HZ(175, 0b011), DR_330HZ(330, 0b100),
		DR_600HZ(600, 0b101), DR_1000HZ(1000, 0b110);

		private int dateRate;
		private byte mask;

		private DataRate(int dateRate, int dataRate) {
			this.dateRate = dateRate;
			this.mask = (byte) (mask << C1_DATA_RATE_BIT_START);
		}

		public int getDataRate() {
			return dateRate;
		}

		byte getMask() {
			return mask;
		}
	}

	public static enum OperatingMode {
		NORMAL(1, 0b0), TURBO(2, 0b1);

		private int multiplier;
		private byte mask;

		private OperatingMode(int multiplier, int mask) {
			this.multiplier = multiplier;
			this.mask = (byte) (mask << C1_OP_MODE_BIT_START);
		}

		public int getMultiplier() {
			return multiplier;
		}

		byte getMask() {
			return mask;
		}
	}

	public static enum ConversionMode {
		SINGLE_SHOT(0b0), CONTINUOUS(0b1);

		private byte mask;

		private ConversionMode(int mask) {
			this.mask = (byte) (mask << C1_CONV_MODE_BIT_START);
		}

		byte getMask() {
			return mask;
		}
	}

	public static enum VRef {
		INTERNAL(0b00), EXTERNAL(0b01), ANALOG_SUPPLY(0b10);

		private byte mask;

		private VRef(int mask) {
			this.mask = (byte) (mask << C1_VREF_BIT_START);
		}

		public int getMask() {
			return mask;
		}
	}

	public static enum TemperatureSensorMode {
		ENABLED(true, 0b1), DISABLED(false, 0b0);

		private boolean enabled;
		private byte mask;

		private TemperatureSensorMode(boolean enabled, int mask) {
			this.enabled = enabled;
			this.mask = (byte) (mask << C1_TEMP_SENSOR_BIT_START);
		}

		public boolean isEnabled() {
			return enabled;
		}

		byte getMask() {
			return mask;
		}
	}

	public static enum DataCounter {
		ENABLED(true, 0b1), DISABLED(false, 0b0);

		private boolean enabled;
		private byte mask;

		private DataCounter(boolean enabled, int mask) {
			this.enabled = enabled;
			this.mask = (byte) (mask << C2_DATA_CNT_EN_BIT_START);
		}

		public boolean isEnabled() {
			return enabled;
		}

		byte getMask() {
			return mask;
		}
	}

	public static enum CrcConfig {
		DISABLED(0b00), INVERTED_DATA_OUTPUT(0b01), CRC16(0b10);

		private byte mask;

		private CrcConfig(int mask) {
			this.mask = (byte) (mask << C2_CRC_EN_BIT_START);
		}

		byte getMask() {
			return mask;
		}
	}

	public static enum BurnoutCurrentSources {
		ENABLED(true, 0b1), DISABLED(false, 0b0);

		private boolean enabled;
		private byte mask;

		private BurnoutCurrentSources(boolean enabled, int mask) {
			this.enabled = enabled;
			this.mask = (byte) (mask << C2_BCS_BIT_START);
		}

		public boolean isEnabled() {
			return enabled;
		}

		byte getMask() {
			return mask;
		}
	}

	public static enum IdacCurrent {
		IDAC_OFF(0, 0b000), IDAC_10UA(10, 0b001), IDAC_50UA(50, 0b010), IDAC_100UA(100, 0b011), IDAC_250UA(250, 0b100),
		IDAC_500UA(500, 0b101), IDAC1000UA(1000, 0b110), IDAC_1500UA(1500, 0b111);

		private int microAmps;
		private byte mask;

		private IdacCurrent(int microAmps, int mask) {
			this.microAmps = microAmps;
			this.mask = (byte) (mask << C2_IDAC_CRNT_BIT_START);
		}

		public int getMicroAmps() {
			return microAmps;
		}

		byte getMask() {
			return mask;
		}
	}

	public static enum Idac1RoutingConfig {
		DISABLED(0b000), AIN0(0b001), AIN1(0b010), AIN2(0b011), AIN3(0b100), REFP(0b101), REFN(0b110);

		private byte mask;

		private Idac1RoutingConfig(int mask) {
			this.mask = (byte) (mask << C3_I1MUX_BIT_START);
		}

		byte getMask() {
			return mask;
		}
	}

	public static enum Idac2RoutingConfig {
		DISABLED(0b000), AIN0(0b001), AIN1(0b010), AIN2(0b011), AIN3(0b100), REFP(0b101), REFN(0b110);

		private byte mask;

		private Idac2RoutingConfig(int mask) {
			this.mask = (byte) (mask << C3_I2MUX_BIT_START);
		}

		byte getMask() {
			return mask;
		}
	}

	/*
	 * The device has four 8-bit configuration registers that are accessible through
	 * the I2C interface using the RREG and WREG commands. After power-up or reset,
	 * all registers are set to the default values (which are all 0). All register
	 * values are retained during power-down mode.
	 */
	private static enum ConfigRegister {
		REG0(0b00), REG1(0b01), REG2(0b10), REG3(0b11);

		private byte mask;

		private ConfigRegister(int mask) {
			this.mask = (byte) (mask << 2);
		}

		byte getMask() {
			return mask;
		}
	}

	/*- Config register 0
	 * MUX 7:4 (R/W), Gain 3:1 (R/W), PGA Bypass 0 (R/W)
	 */
	private static final int C0_MUX_BIT_START = 4;
	private static final int C0_GAIN_BIT_START = 1;
	private static final int C0_PGA_BYPASS_BIT_START = 0;
	/*- Config register 1
	 * Data Rate 7:5 (R/W), Operating Mode 4 (R/W), Conversion Mode 3 (R/W),
	 * VRef 2:1 (R/W), Temp. Sensor Mode 0 (R/W)
	 */
	private static final int C1_DATA_RATE_BIT_START = 5;
	private static final int C1_OP_MODE_BIT_START = 4;
	private static final int C1_CONV_MODE_BIT_START = 3;
	private static final int C1_VREF_BIT_START = 1;
	private static final int C1_TEMP_SENSOR_BIT_START = 0;
	/*- Config register 2
	 * Data Ready 7 (R), Data Counter Enable 6 (R/W), CRC Enable 5:4 (R/W),
	 * Burn-out Current Sources 3 (R/W), IDAC Current Setting 0 (R/W)
	 */
	private static final int C2_DATA_RDY_BIT_START = 7;
	private static final int C2_DATA_CNT_EN_BIT_START = 6;
	private static final int C2_CRC_EN_BIT_START = 4;
	private static final int C2_BCS_BIT_START = 3;
	private static final int C2_IDAC_CRNT_BIT_START = 0;
	/*- Config register 3
	 * I1MUX 7:5 (R/W), I2MUX 4:2 (R/W), Reserved 1:0 (R) Always 0
	 */
	private static final int C3_I1MUX_BIT_START = 5;
	private static final int C3_I2MUX_BIT_START = 2;

	private static final byte COMMAND_RESET = (byte) 0b00000110;
	private static final byte COMMAND_START = (byte) 0b00001000;
	private static final byte COMMAND_POWER_DOWN = (byte) 0b00000010;
	private static final byte COMMAND_RDATA = (byte) 0b00010000;
	private static final byte COMMAND_READ_REG = (byte) 0b00100000;
	private static final byte COMMAND_WRITE_REG = (byte) 0b01000000;

	private static final int CRC_POLYNOMIAL = 0b10001000000100001;
	private static final int CRC_INIT = 0xffff;

	public static class Builder {
		private Address address;
		private GainConfig gainConfig = GainConfig.GAIN_1;
		private PgaBypass pgaBypass = PgaBypass.ENABLED;
		private DataRate dataRate = DataRate.DR_20HZ;
		private OperatingMode operatingMode = OperatingMode.NORMAL;
		private VRef vRef = VRef.INTERNAL;
		private TemperatureSensorMode tsMode = TemperatureSensorMode.DISABLED;
		private DataCounter dataCounter;
		private CrcConfig crcConfig;
		private BurnoutCurrentSources burnoutCurrentSources;
		private IdacCurrent idacCurrent;
		private Idac1RoutingConfig idac1RoutingConfig;
		private Idac2RoutingConfig idac2RoutingConfig;

		protected Builder(Address address) {
			this.address = address;
		}

		public Builder setGainConfig(GainConfig gainConfig) {
			this.gainConfig = gainConfig;
			return this;
		}

		public Builder setPgaBypass(PgaBypass pgaBypass) {
			this.pgaBypass = pgaBypass;
			return this;
		}

		public Builder setPgaBypassEnabled(boolean pgaBypassEnabled) {
			this.pgaBypass = pgaBypassEnabled ? PgaBypass.ENABLED : PgaBypass.DISABLED;
			return this;
		}

		public Builder setDataRate(DataRate dataRate) {
			this.dataRate = dataRate;
			return this;
		}

		public Builder setOperatingMode(OperatingMode operatingMode) {
			this.operatingMode = operatingMode;
			return this;
		}

		public Builder setTurboModeEnabled(boolean turboModeEnabled) {
			this.operatingMode = turboModeEnabled ? OperatingMode.TURBO : OperatingMode.NORMAL;
			return this;
		}

		public Builder setVRef(VRef vRef) {
			this.vRef = vRef;
			return this;
		}

		public Builder setTemperatureSensorMode(TemperatureSensorMode tsMode) {
			this.tsMode = tsMode;
			return this;
		}

		public Builder setTemperatureSensorEnabled(boolean tsEnabled) {
			this.tsMode = tsEnabled ? TemperatureSensorMode.ENABLED : TemperatureSensorMode.DISABLED;
			return this;
		}

		public Builder setDataCounter(DataCounter dataCounter) {
			this.dataCounter = dataCounter;
			return this;
		}

		public Builder setDataCounterEnabled(boolean dcEnabled) {
			this.dataCounter = dcEnabled ? DataCounter.ENABLED : DataCounter.DISABLED;
			return this;
		}

		public Builder setCrcConfig(CrcConfig crcConfig) {
			this.crcConfig = crcConfig;
			return this;
		}

		public Builder setBurnoutCurrentSources(BurnoutCurrentSources burnoutCurrentSources) {
			this.burnoutCurrentSources = burnoutCurrentSources;
			return this;
		}

		public Builder setBurnoutCurrentSourcesEnabled(boolean burnoutCurrentSourcesEnabled) {
			this.burnoutCurrentSources = burnoutCurrentSourcesEnabled ? BurnoutCurrentSources.ENABLED
					: BurnoutCurrentSources.DISABLED;
			return this;
		}

		public Builder setIdacCurrent(IdacCurrent idacCurrent) {
			this.idacCurrent = idacCurrent;
			return this;
		}

		public Builder setIdac1RoutingConfig(Idac1RoutingConfig idac1RoutingConfig) {
			this.idac1RoutingConfig = idac1RoutingConfig;
			return this;
		}

		public Builder setIdac2RoutingConfig(Idac2RoutingConfig idac2RoutingConfig) {
			this.idac2RoutingConfig = idac2RoutingConfig;
			return this;
		}

		public Ads112C04 build() {
			return new Ads112C04(address, gainConfig, pgaBypass, dataRate, operatingMode, vRef, tsMode, dataCounter,
					crcConfig, burnoutCurrentSources, idacCurrent, idac1RoutingConfig, idac2RoutingConfig);
		}
	}

	public static Builder builder(Address address) {
		return new Builder(address);
	}

	private I2CDevice device;
	private GainConfig gainConfig;
	private PgaBypass pgaBypass;
	private DataRate dataRate;
	private OperatingMode operatingMode;
	private ConversionMode conversionMode;
	private VRef vRef;
	private TemperatureSensorMode tsMode;
	private DataCounter dataCounter;
	private CrcConfig crcConfig;
	private BurnoutCurrentSources burnoutCurrentSources;
	private IdacCurrent idacCurrent;
	private Idac1RoutingConfig idac1RoutingConfig;
	private Idac2RoutingConfig idac2RoutingConfig;
	private byte mux;

	protected Ads112C04(Address address, GainConfig gainConfig, PgaBypass pgaBypass, DataRate dataRate,
			OperatingMode operatingMode, VRef vRef, TemperatureSensorMode tsMode, DataCounter dataCounter,
			CrcConfig crcConfig, BurnoutCurrentSources burnoutCurrentSources, IdacCurrent idacCurrent,
			Idac1RoutingConfig idac1RoutingConfig, Idac2RoutingConfig idac2RoutingConfig) {
		this.gainConfig = gainConfig;
		this.pgaBypass = pgaBypass;
		this.dataRate = dataRate;
		this.operatingMode = operatingMode;
		this.vRef = vRef;
		this.tsMode = tsMode;
		this.dataCounter = dataCounter;
		this.crcConfig = crcConfig;
		this.burnoutCurrentSources = burnoutCurrentSources;
		this.idacCurrent = idacCurrent;
		this.idac1RoutingConfig = idac1RoutingConfig;
		this.idac2RoutingConfig = idac2RoutingConfig;
		
		mux = (byte) ((0b1000) << C0_MUX_BIT_START);

		conversionMode = ConversionMode.SINGLE_SHOT;

		device = I2CDevice.builder(address.getValue()).setByteOrder(ByteOrder.BIG_ENDIAN).build();

		reset();
		
		setConfig0();
		setConfig1();
		setConfig2();
		setConfig3();
	}

	public void reset() {
		device.writeByte(COMMAND_RESET);
	}

	public void start() {
		device.writeByte(COMMAND_START);
	}

	private byte readConfigRegister(ConfigRegister register) {
		return device.readByteData(COMMAND_READ_REG | register.getMask());
	}

	private void writeConfigRegister(ConfigRegister register, byte value) {
		device.writeByteData(COMMAND_WRITE_REG | register.getMask(), value);
	}

	private void setConfig0() {
		writeConfigRegister(ConfigRegister.REG0, (byte) (mux | gainConfig.getMask() | pgaBypass.getMask()));
	}

	private void setConfig1() {
		writeConfigRegister(ConfigRegister.REG1, (byte) (dataRate.getMask() | operatingMode.getMask()
				| conversionMode.getMask() | vRef.getMask() | tsMode.getMask()));
	}

	private void setConfig2() {
		writeConfigRegister(ConfigRegister.REG2, (byte) (dataCounter.getMask() | crcConfig.getMask()
				| burnoutCurrentSources.getMask() | idacCurrent.getMask()));
	}

	private void setConfig3() {
		writeConfigRegister(ConfigRegister.REG3, (byte) (idac1RoutingConfig.getMask() | idac2RoutingConfig.getMask()));
	}

	public short readDataWhenAvailable() {
		while (true) {
			if ((readConfigRegister(ConfigRegister.REG2) & (1 << C2_DATA_RDY_BIT_START)) != 0) {
				break;
			}
			// 10 uS
			SleepUtil.busySleep(10_000);
		}

		// XXX Note that if the conversion counter is enabled 3 bytes need to be read,
		// the first byte is the conversion counter
		// XXX Note that if CRC checks are enabled, an additional 2 bytes need to be
		// read after the data - CRC MSB & MSC LSB
		int bytes_to_read = 2;
		if (dataCounter.isEnabled()) {
			bytes_to_read++;
		}
		if (!crcConfig.equals(CrcConfig.DISABLED)) {
			bytes_to_read += 2;
		}

		ByteBuffer bb = device.readI2CBlockDataByteBuffer(COMMAND_RDATA, bytes_to_read);
		if (dataCounter.isEnabled()) {
			int counter = bb.get() & 0xff;
			Logger.debug("Conversion counter: {}", Integer.valueOf(counter));
		}
		short value = bb.getShort();
		if (!crcConfig.equals(CrcConfig.DISABLED)) {
			short crc_val = bb.getShort();
			Logger.debug("Got CRC {} for data {}; CRC mode: {}", Short.valueOf(crc_val), Short.valueOf(value),
					crcConfig);
			// TODO Validate the CRC value
			if (crcConfig == CrcConfig.INVERTED_DATA_OUTPUT) {
				// A bitwise-inverted version of the data
				short calc_crc_val = (short) (~value);
				if (calc_crc_val != crc_val) {
					Logger.warn("CRC error for value {}, calculated {}, got {}", Short.valueOf(value),
							Short.valueOf((calc_crc_val)), Short.valueOf(crc_val));
				}
			} else if (crcConfig == CrcConfig.CRC16) {
				// In CRC mode, the checksum bytes are the 16-bit remainder of the bitwise
				// exclusive-OR (XOR) of the data bytes with a CRC polynomial
				// The CRC is based on the CRC-16-CCITT polynomial: x16 + x12 + x5 + 1 with an
				// initial value of FFFFh.
				short calc_crc_val = (short) Crc.crc16Ccitt(CRC_POLYNOMIAL, CRC_INIT, value);
				if (calc_crc_val != crc_val) {
					Logger.warn("CRC error for value {}, calculated {}, got {}", Short.valueOf(value),
							Short.valueOf((calc_crc_val)), Short.valueOf(crc_val));
				}
			}
		}

		return value;
	}

	public short getValueSingle(int adcNumber) {
		conversionMode = ConversionMode.SINGLE_SHOT;
		setConfig1();
		// Compare against ground (AINn - AVSS)
		// For settings where AINN = AVSS, the PGA must be disabled (PGA_BYPASS = 1) and
		// only gains 1, 2, and 4 can be used.
		gainConfig = GainConfig.GAIN_1;
		pgaBypass = PgaBypass.DISABLED;
		mux = (byte) ((0b1000 + adcNumber) << C0_MUX_BIT_START);
		setConfig0();
		
		return readDataWhenAvailable();
	}
}
