/*
 * *****************************************************************************
 * Copyright (C) 2014-2023 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * ****************************************************************************
 */
package io.github.dsheirer.source.tuner.rtl.fc0012;

import io.github.dsheirer.source.SourceException;
import io.github.dsheirer.source.tuner.TunerType;
import io.github.dsheirer.source.tuner.configuration.TunerConfiguration;
import io.github.dsheirer.source.tuner.rtl.EmbeddedTuner;
import io.github.dsheirer.source.tuner.rtl.RTL2832TunerController;
import java.text.DecimalFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.usb4java.LibUsbException;

public class FC0012EmbeddedTuner extends EmbeddedTuner
{
    private final static Logger mLog = LoggerFactory.getLogger(FC0012EmbeddedTuner.class);
    private DecimalFormat FREQUENCY_FORMAT = new DecimalFormat("0.000000");
    private static final long MINIMUM_SUPPORTED_FREQUENCY = 21_733_400;
    private static final long MAXIMUM_SUPPORTED_FREQUENCY = 947_733_400l;
    private static final double USABLE_BANDWIDTH_PERCENT = 0.95;
    private static final int DC_SPIKE_AVOID_BUFFER = 15000;
    private static final byte I2C_ADDRESS = (byte) 0xC6;
    private static final int XTAL_FREQUENCY = 28_800_000;
    private static final int XTAL_FREQUENCY_DIVIDED_BY_2 = XTAL_FREQUENCY / 2;
    private static byte[] REGISTERS = {(byte) 0x00, (byte) 0x05, (byte) 0x10, (byte) 0x00, (byte) 0x00, (byte) 0x0f,
            (byte) 0x00, (byte) 0x00, (byte) 0xff, (byte) 0x6e, (byte) 0xb8, (byte) 0x82, (byte) 0xfc, (byte) 0x02,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x1f, (byte) 0x08, (byte) 0x00, (byte) 0x04};

    private long mTunedFrequency = MINIMUM_SUPPORTED_FREQUENCY;

    public FC0012EmbeddedTuner(RTL2832TunerController.ControllerAdapter adapter)
    {
        super(adapter);
    }

    @Override
    public TunerType getTunerType()
    {
        return TunerType.FITIPOWER_FC0012;
    }

    @Override
    public long getMinimumFrequencySupported()
    {
        return MINIMUM_SUPPORTED_FREQUENCY;
    }

    @Override
    public long getMaximumFrequencySupported()
    {
        return MAXIMUM_SUPPORTED_FREQUENCY;
    }

    @Override
    public int getDcSpikeHalfBandwidth()
    {
        return DC_SPIKE_AVOID_BUFFER;
    }

    @Override
    public double getUsableBandwidthPercent()
    {
        return USABLE_BANDWIDTH_PERCENT;
    }

    @Override
    public void apply(TunerConfiguration tunerConfig) throws SourceException
    {
        if(tunerConfig instanceof FC0012TunerConfiguration config)
        {
            try
            {
                setGain(config.getAGC(), config.getLnaGain());
            }
            catch(Exception e)
            {
                throw new SourceException("Error while applying tuner config", e);
            }
        }
        else
        {
            throw new IllegalArgumentException("Unrecognized tuner config [" + tunerConfig.getClass() + "]");
        }
    }

    private void write(Register register, byte value, boolean controlI2CRepeater)
    {
        getAdapter().writeI2CRegister(I2C_ADDRESS, register.byteValue(), value, controlI2CRepeater);
    }

    public void writeMaskedRegister(Register register, byte value, byte mask, boolean controlI2CRepeater)
    {
        byte content = (byte) (readRegister(register, controlI2CRepeater) & 0xFF);
        content &= mask;
        content |= value;
        write(register, content, controlI2CRepeater);
    }

    private void write(Field field, byte value, boolean controlI2CRepeater)
    {
        writeMaskedRegister(field.getRegister(), value, field.getMask(), controlI2CRepeater);
    }

    private int readRegister(Register register, boolean controlI2CRepeater)
    {
        return getAdapter().readI2CRegister(I2C_ADDRESS, register.byteValue(), controlI2CRepeater);
    }

    @Override
    public void setSamplingMode(RTL2832TunerController.SampleMode mode) throws LibUsbException
    {
    }

    @Override
    public void setSampleRateFilters(int sampleRate) throws SourceException
    {
    }

    public long getTunedFrequency() throws SourceException
    {
        return mTunedFrequency;
    }

    @Override
    public synchronized void setTunedFrequency(long frequency) throws SourceException
    {
        getAdapter().getLock().lock();

        try
        {
            boolean i2CEnabledState = getAdapter().isI2CRepeaterEnabled();
            if(!i2CEnabledState)
            {
                getAdapter().enableI2CRepeater();
            }

            FrequencyDivider divider = FrequencyDivider.fromFrequency(frequency);
            boolean vcoSelect = ((double)frequency / (double)divider.getIntegralFrequencyMultiplier()) >= 212.5;
            int integral = (int)(frequency / divider.getIntegralFrequencyMultiplier());
            int pm = integral / 8;
            pm = Math.max(pm, 11);
            pm = Math.min(pm, 31);

            int am = integral - (pm * 8);
            if(am < 2)
            {
                am += 8;
                pm--;
            }
            am = Math.min(am, 15);
            integral = pm * 8 + am;

            int residual = (int)(frequency - (integral * divider.getIntegralFrequencyMultiplier()));

            int fractional = (int)Math.round(residual / divider.getFractionalFrequencyMultiplier());

            if(pm < 11 || pm > 31 || am < 2 || am > 15 || fractional < 0 || fractional > 65535)
            {
                String message = "FC0012 - no valid PLL combination for frequency [" + frequency + "] using divider [" +
                        divider + "] pm [" + pm + "] am [" + am + "] fractional [" + fractional + "]";
                mLog.error(message);
                throw new SourceException(message);
            }

            setFrequencyValues(divider, pm, am, fractional, vcoSelect);

            if(!i2CEnabledState)
            {
                getAdapter().disableI2CRepeater();
            }
        }
        catch(LibUsbException e)
        {
            mLog.error("FC0012 tuner error while setting tuned frequency [" + frequency + "]", e);
        }
        finally
        {
            getAdapter().getLock().unlock();
        }

        mTunedFrequency = frequency;
    }

    protected void initTuner()
    {
        getAdapter().enableI2CRepeater();
        boolean i2CRepeaterControl = false;

        for(int x = 1; x < REGISTERS.length; x++)
        {
            write(Register.values()[x], REGISTERS[x], i2CRepeaterControl);
        }

        write(Register.R13, (byte)0x0A, false);
        getAdapter().disableI2CRepeater();
    }

    private boolean setFrequencyValues(FrequencyDivider divider, int pm, int am, int fractional, boolean vcoSelect)
            throws SourceException
    {
        if(pm < 11 || pm > 31)
        {
            throw new IllegalArgumentException("PM value [" + pm + "] must be in range 11-31");
        }
        if(am < 2 || am > 15)
        {
            throw new IllegalArgumentException("AM value [" + am + "] must be in range 2-15");
        }
        if(fractional < 0 || fractional > 65535)
        {
            throw new IllegalArgumentException("Fractional value [" + fractional + "] must be in range 0-65,535");
        }
        double exactFrequency = divider.calculate(pm, am, fractional);
        long frequency = (long)exactFrequency;
        byte register5 = divider.getRegister5();
        register5 |= 0x07;

        byte register6 = divider.getRegister6();
        if(vcoSelect)
        {
            register6 |= 0x08;
        }

        write(Register.R01, (byte)(am & 0xFF), false);
        write(Register.R02, (byte)(pm & 0xFF), false);

        write(Register.R03, (byte)((fractional >> 8) & 0xFF), false);
        write(Register.R04, (byte)(fractional & 0xFF), false);

        write(Register.R05, register5, false);
        write(Register.R06, register6, false);

        int tmp = readRegister(Register.R11, false);

        {
            write(Register.R11, (byte)(tmp & 0xFB), false);
        }

        write(Register.R0E, (byte)0x80, false);
        write(Register.R0E, (byte)0x00, false);
        write(Register.R0E, (byte)0x00, false);
        int calibration = readRegister(Register.R0E, false) & 0x3F;

        boolean recalibrateRequired = false;
        if(vcoSelect && calibration > 0x3C)
        {
            register6 &= ~0x08;
            recalibrateRequired = true;
        }
        else if(!vcoSelect && calibration < 0x02)
        {
            register6 |= 0x08;
            recalibrateRequired = true;
        }

        if(recalibrateRequired)
        {
            write(Register.R06, register6, false);
            write(Register.R0E, (byte)0x80, false);
            write(Register.R0E, (byte)0x00, false);
            write(Register.R0E, (byte)0x00, false);
            calibration = readRegister(Register.R0E, false) & 0x3F;
            if((!vcoSelect & calibration < 0x02) || (vcoSelect & calibration > 0x3C))
            {
                String msg = "Unable to tune frequency [" + fractional + "] PLL calibration [" + Integer.toHexString(calibration).toUpperCase() + "] out of limits [02-3C]";
                mLog.error(msg);
                throw new SourceException(msg);
            }
        }

        return true;
    }

    public void setGain(boolean agc, LNAGain manualGain)
    {
        getAdapter().getLock().lock();

        try
        {
            boolean repeaterState = getAdapter().isI2CRepeaterEnabled();
            if(!repeaterState)
            {
                getAdapter().enableI2CRepeater();
            }

            if(agc)
            {
                write(Register.R13, (byte)0x0A, false);
            }
            else
            {
                write(Field.R14_LNA_GAIN, manualGain.byteValue(), false);
                write(Register.R13, (byte)0x0A, false);
            }

            if(!repeaterState)
            {
                getAdapter().disableI2CRepeater();
            }
        }
        finally
        {
            getAdapter().getLock().unlock();
        }
    }

    private enum Register
    {
        R00, R01, R02, R03, R04, R05, R06, R07, R08, R09, R0A, R0B, R0C, R0D, R0E, R0F, R10, R11, R12, R13, R14, R15,
        R16, R17, R18, R19, R1A, R1B, R1C, R1D;

        public byte byteValue()
        {
            return (byte) ordinal();
        }
    }

    private enum Field
    {
        R06_BANDWIDTH(Register.R06, 0xC0),
        R14_LNA_GAIN(Register.R14, 0xE0);

        private Register mRegister;
        private byte mMask;

        Field(Register register, int mask)
        {
            mRegister = register;
            mMask = (byte) mask;
        }

        public Register getRegister()
        {
            return mRegister;
        }

        public byte getMask()
        {
            return mMask;
        }
    }

    public enum LNAGain
    {
        G00("-9.9db", 0x02),
        G01("7.1db", 0x08),
        G02("17.9db", 0x17),
        G03("19.2db", 0x10);

        private String mLabel;
        private byte mValue;

        LNAGain(String label, int value)
        {
            mLabel = label;
            mValue = (byte) value;
        }

        public byte byteValue()
        {
            return mValue;
        }

        @Override
        public String toString()
        {
            return mLabel;
        }
    }

    public enum FrequencyDivider
    {
          D96(96, true, 0x82, 0x00, 13_500_000, 39_749_997),
          D64(64, false, 0x82, 0x02, 20_500_000, 59_624_996),
          D48(48, true, 0x42, 0x00, 27_000_000, 79_499_995),
          D32(32, false, 0x42, 0x02, 40_500_000, 119_249_993),
          D24(24, true, 0x22, 0x00, 54_000_000, 158_999_990),
          D16(16, false, 0x22, 0x02, 81_000_000, 238_499_986),
          D12(12, true, 0x12, 0x00, 108_000_000, 317_999_981),
          D08(8, false, 0x12, 0x02, 162_000_000, 476_999_972),
          D06(6, true, 0x0A, 0x00, 235_200_000, 635_999_963),
          D04(4, false, 0x0A, 0x02, 514_900_000, 953_999_945);

        private int mDivider;
        private boolean mIs3xMode;
        private int mRegister5;
        private long mMinimumFrequency;
        private long mMaximumFrequency;

        FrequencyDivider(int divider, boolean is3xMode, int register5, int register6, long minimumFrequency, long maximumFrequency)
        {
            mDivider = divider;
            mIs3xMode = is3xMode;
            mRegister5 = register5;
            mMinimumFrequency = minimumFrequency;
            mMaximumFrequency = maximumFrequency;
        }

        public byte getRegister5()
        {
            return (byte)(mRegister5 & 0xFF);
        }

        public byte getRegister6()
        {
            return is3xMode() ? (byte)0xA0 : (byte)0xA2;
        }

        public double getFrequency(int pm, int am, int fractional, boolean vcoSelect)
        {
            if(vcoSelect)
            {
                return calculate(pm, am, fractional);
            }
            else
            {
                return calculate(pm, am - 2, fractional);
            }
        }

        public boolean isVcoSelect(double frequency)
        {
            return (frequency * getDivider()) > 3_060_000_000l;
        }

        private double calculate(int pm, int am, int fractional)
        {
            return (((8l * pm) + am) * getIntegralFrequencyMultiplier()) + (getFractionalFrequencyMultiplier() * fractional);
        }

        public int getIntegralFrequencyMultiplier()
        {
            return XTAL_FREQUENCY_DIVIDED_BY_2 / mDivider;
        }

        public double getFractionalFrequencyMultiplier()
        {
            return XTAL_FREQUENCY / mDivider / 65_536d;
        }

        public boolean is3xMode()
        {
            return mIs3xMode;
        }

        public int getDivider()
        {
            return mDivider;
        }

        public long getMinimumFrequency()
        {
            return mMinimumFrequency;
        }

        public long getMaximumFrequency()
        {
            return mMaximumFrequency;
        }

        public boolean contains(long frequency)
        {
            return mMinimumFrequency <= frequency && frequency <= mMaximumFrequency;
        }

        public static FrequencyDivider fromFrequency(long frequency)
        {
            for(FrequencyDivider divider : FrequencyDivider.values())
            {
                if(divider.contains(frequency))
                {
                    return divider;
                }
            }

            return FrequencyDivider.D16;
        }
    }
}
