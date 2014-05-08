package com.uboxol.serialcomport;


public class DataTransfer {
    private byte[] DataBuffer = new byte[64];
    private int DataBufferLen = 0;

    public DataTransfer(){
        reset();
    }

    public void reset()
    {
        DataBuffer = new byte[64];
        DataBufferLen = 0;
    }


    public static byte[] getDataBuffer(int who,byte[] datas, int len)
    {
        byte[] dataBuffer = new byte[len + 3];

        dataBuffer[0] = (byte)0xA5;
        dataBuffer[1] = (byte)(who & 0xff);
        dataBuffer[2] = (byte)(len & 0xff);
        int WritePos = 3;
        for (int i = 0; i < len ; i++)
        {
            dataBuffer[ WritePos ++] = datas[i];
        }
        return dataBuffer;
    }

    private boolean checkData()
    {
        return !(DataBufferLen > 0 && (DataBuffer[0] & 0xff) != 0xA5) &&
                !(DataBufferLen > 1 && (DataBuffer[1] & 0xff) > 0x05) &&
                !(DataBufferLen > 2 && (DataBuffer[2] & 0xff) > 0x3C);
    }

    public int getWho() {
        return (int)DataBuffer[1];
    }


    public int getLen() {
        return (int)DataBuffer[2];
    }

    public byte[] getDatas() {

        int length = (int)DataBuffer[2];
        byte[] data = new byte[length];
        System.arraycopy( DataBuffer, 3, data, 0, length);
        return data;
    }

    public boolean AddData(byte mbyte) {
        if(!checkData())
        {
            DataBufferLen = 0;
        }

        if( DataBufferLen < 63){
            DataBuffer[ DataBufferLen++]=mbyte;
        }
        return DataBufferLen >= getLen() + 3;
    }

}