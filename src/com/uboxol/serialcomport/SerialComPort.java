package com.uboxol.serialcomport;

import android.util.Log;


public class SerialComPort {

    /**
     * <p>串口ID</p>
     * @author  chenpeng
     * @version Version 0.1
     */
    public static enum COM_ID{
        /**
         * 串口1
         */
        COM_1(1),
        /**
         * 串口2
         */
        COM_2(2),
        /**
         * 串口3
         */
        COM_3(3),
        /**
         * 串口4
         */
        COM_4(4),
        /**
         * 串口5
         */
        COM_5(5);
        private int value;
        COM_ID(int v)
        {
            value = v;
        }
        int getValue()
        {
            return this.value;
        }
    }
    /**
     * <p>停止位</p>
     * @author  chenpeng
     * @version Version 0.1
     */
    public static enum STOP_BITS{
        /**
         * 1bit 停止位
         */
        BIT_1(0),
        /**
         * 1.5 bit 停止位
         */
        BIT_1_5(1),
        /**
         * 2 bit 停止位
         */
        BIT_2(2);
        private int value;
        STOP_BITS(int v)
        {
            this.value = v;
        }
        int getValue()
        {
            return this.value;
        }
    }
    /**
     * <p>校验位</p>
     * @author  chenpeng
     * @version Version 0.1
     */
    public static enum PARITY{
        /**
         * none 校验位
         */
        NONE(0),
        /**
         * Odd 校验位
         */
        ODD(1),
        /**
         * Even 校验位
         */
        EVEN(2);
        private int value;
        PARITY(int v)
        {
            value = v;
        }
        int getValue()
        {
            return this.value;
        }
    }
    /**
     * <p>数据位</p>
     * @author  chenpeng
     * @version Version 0.1
     */
    public static enum DATA_BITS{
        /**
         * 8bit 数据位
         */
        BIT_8(8),
        /**
         * 7bit 数据位
         */
        BIT_7(7);
        private int value;
        DATA_BITS(int v)
        {
            value = v;
        }
        int getValue()
        {
            return this.value;
        }
    }

    /**
     * <p>串口打开状态</p>
     * @author  chenpeng
     * @version Version 0.1
     */
    public static enum SerialComPortStatus {
        /**
         * 串口没有连接状态
         */
        NOT_CONNECT,
        /**
         * 串口已经连接状态
         */
        CONNECTED,
        /**
         * USB设备没有打开
         */
        DEVICE_NOT_CONNECT,
        /**
         * 没有获取到USB访问权限
         */
        DEVICE_NO_PERMISSION
    }

    private int baudRate = 9600;   // 110 / 300 / 600 / 9600/ 115200 /256000

    private STOP_BITS stopBits = STOP_BITS.BIT_1;   //0=1stop bit, 1=1.5 stop bit, 2=2 stop bit;

    private PARITY parity = PARITY.NONE;     //0=none, 1=Odd(奇校验), 2=Even(偶校验)

    private COM_ID portId = COM_ID.COM_1;

    private DATA_BITS dataBits = DATA_BITS.BIT_8;   //7 or 8

    private SerialComPortStatus serialComPortStatus;

    /**
     * 可以发送的最大的数据量
     */
    public int maxSendLength = 0;

    private MessageBuffer sendBuffer = null;

    private MessageBuffer readBuffer = null;


    SerialComPort(COM_ID com)
    {
        this.portId = com;
        this.stopBits = STOP_BITS.BIT_1;
        this.baudRate = 9600;
        this.dataBits = DATA_BITS.BIT_8;
        this.parity = PARITY.NONE;
        serialComPortStatus = SerialComPortStatus.NOT_CONNECT;
    }

    public void send( byte[] b, int offset, int len) throws WriteSerialDataException {
        if (sendBuffer!= null)
        {
            sendBuffer.put(b, offset, len);
        }
        else
        {
            throw new WriteSerialDataException("Can't send data to not open SerialComPort !");
        }
    }

    public int getSendData( byte[] b, int offset, int len)
    {
        if (sendBuffer!= null)
        {
            return sendBuffer.read(b, offset, len);
        }
        return 0;
    }

    public void putReadData(byte[] b, int offset, int len)
    {
        if (readBuffer!= null)
        {
            readBuffer.put(b, offset, len);
        }
    }
    public int read( byte[] b, int offset, int len) throws ReadSerialDataException{
        if (readBuffer != null)
        {
            return readBuffer.read(b,offset,len);
        }
        else
        {
            throw new ReadSerialDataException("Can't read data from not open SerialComPort !");
        }
    }
    public SerialComPortStatus status() {
        return serialComPortStatus;
    }

    public void setStatus(SerialComPortStatus status) {
        Log.d("serialComPortStatus change",this.serialComPortStatus.toString() + " to " + status.toString());
        this.serialComPortStatus = status;
    }

    public boolean isConnected()
    {
        return serialComPortStatus == SerialComPortStatus.CONNECTED;
    }


    public void open(int baudRate, STOP_BITS stopBits, DATA_BITS dataBits, PARITY parity)
    {
        this.stopBits = stopBits;
        this.baudRate = baudRate;
        this.dataBits = dataBits;
        this.parity = parity;
        this.readBuffer = new MessageBuffer( 5 * this.baudRate);
        this.sendBuffer = new MessageBuffer( 0x200000);
        this.serialComPortStatus = SerialComPortStatus.CONNECTED;
    }
    public void close()
    {
        this.readBuffer = null;
        this.sendBuffer = null;
        this.serialComPortStatus = SerialComPortStatus.NOT_CONNECT;
    }

    /**
     * GET 7 BYTES ARRAY
     * @return 7 BYTES ARRAY
     */
    public byte[] getSerialBytes()
    {
        /**
         * uint32_t bitrate; //110 / 300 / 9600/ 115200 ...
         * uint8_t stop_bits; //0=1stop bit, 1=1.5 stop bit, 2=2 stop bit;
         * uint8_t paritytype; //0=none, 1=Odd(奇校验), 2=Even(偶校验)
         * uint8_t datatype; //7 or 8 (高3位代表串口编号:1~5 代表串口1到串口5)
         */

        byte[] dataBuffer = new byte[7];

        byte[] temp = toLH(this.baudRate);
        System.arraycopy(temp, 0, dataBuffer, 0, temp.length);

        temp = new byte[1];
        temp[0] = (byte)( this.stopBits.getValue() & 0xff);

        System.arraycopy(temp, 0, dataBuffer, 4, temp.length);


        temp[0] = (byte)( this.parity.getValue() & 0xff);
        System.arraycopy(temp, 0, dataBuffer, 5, temp.length);


        temp[0] = (byte)( ( ( this.portId.getValue() << 5 ) | this.dataBits.getValue() ) & 0xff);
        System.arraycopy(temp, 0, dataBuffer, 6, temp.length);

        return dataBuffer;

    }

    public COM_ID getPortId() {
        return portId;
    }

    public int getSendBufferSize()
    {
        return this.sendBuffer.getSize();
    }
    /**
     * 将int转为低字节在前，高字节在后的byte数组
     */
    private byte[] toLH(int n) {
        byte[] b = new byte[4];
        b[0] = (byte) (n & 0xff);
        b[1] = (byte) (n >> 8 & 0xff);
        b[2] = (byte) (n >> 16 & 0xff);
        b[3] = (byte) (n >> 24 & 0xff);
        return b;
    }
}