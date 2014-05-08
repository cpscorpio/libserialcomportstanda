package com.uboxol.serialcomport;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.*;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.*;


/**
 * <p>端口控制类</p>
 * @author  chenpeng
 * @version 0.1
 */
public class SerialComPortControl {


    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private enum DeviceStatus {
        NOT_CONNECT,
        NO_PERMISSION,
        CONNECTED
    }

    private static final int SEND_CTR_TIMEOUT = 100;
    private static final int SEND_CTR_REQ_Type = 0x21;
    private static final int SEND_CTR_REQ = 0x20;

    private static final int RECV_CTR_LEN_REQ = 0x24;

    DataTransfer readDateTransfer = null;

    private final int deviceVendorId = 1155;
    private final int deviceProductId = 22336;

    private UsbManager usbManager = null;
    private UsbDevice usbDevice = null;
    private UsbInterface[] usbInterface = null;
    private UsbEndpoint[][] usbEndPoint = new UsbEndpoint[5][5];
    private UsbDeviceConnection usbDeviceConnection = null;

    private Context context = null;


    private List<SerialComPort> portList ;

    private DeviceStatus status = DeviceStatus.NOT_CONNECT;



    ReadUsbMessageThread readThread = null;

    SendMessageHandler sendMessageCOM1Handler = new SendMessageHandler(SerialComPort.COM_ID.COM_1);
    SendMessageHandler sendMessageCOM2Handler = new SendMessageHandler(SerialComPort.COM_ID.COM_2);
    SendMessageHandler sendMessageCOM3Handler = new SendMessageHandler(SerialComPort.COM_ID.COM_3);
    SendMessageHandler sendMessageCOM4Handler = new SendMessageHandler(SerialComPort.COM_ID.COM_4);
    SendMessageHandler sendMessageCOM5Handler = new SendMessageHandler(SerialComPort.COM_ID.COM_5);

    private List<String> listeningActions;


    /**
     * 唯一构造函数
     * @param context android.content.Context
     */
    public SerialComPortControl(Context context)
    {
        portList = new ArrayList<SerialComPort>();
        portList.add( new SerialComPort(SerialComPort.COM_ID.COM_1));
        portList.add( new SerialComPort(SerialComPort.COM_ID.COM_2));
        portList.add( new SerialComPort(SerialComPort.COM_ID.COM_3));
        portList.add( new SerialComPort(SerialComPort.COM_ID.COM_4));
        portList.add( new SerialComPort(SerialComPort.COM_ID.COM_5));
        Log.d("Port List ", "" + portList.size());
        this.context = context;
        this.usbManager = (UsbManager) this.context.getSystemService(Context.USB_SERVICE);
        listeningActions = new ArrayList<String>();
        listeningActions.add(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        listeningActions.add(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        listeningActions.add(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        listeningActions.add(UsbManager.ACTION_USB_DEVICE_DETACHED);

    }
    /**
     * open 方法简述
     * <p>打开串口函数</p>
     * @param com_id        串口ID [ 1 ~ 5]
     * @param baundRate     比特率 [ 110, 300, 600, 9600, 115200, ...]
     * @param data_bits     数据位 [ 7, 8]
     * @param stop_bits     停止位 [ 1, 1.5, 2]
     * @param parity        校验位 [ none, Odd(奇校验), Even(偶校验)]
     * @return  串口的打开状态
     * @exception NullPointerException com_id 为空时抛出
     */
    public SerialComPort.SerialComPortStatus open( SerialComPort.COM_ID com_id, int baundRate, SerialComPort.DATA_BITS data_bits,
                                                   SerialComPort.STOP_BITS stop_bits, SerialComPort.PARITY parity)
    {
        if (com_id == null) throw new NullPointerException("com_id is null");
        SerialComPort port = serialComPort(com_id);
        switch (getDeviceStatus())
        {
            case NOT_CONNECT:{
                port.setStatus( SerialComPort.SerialComPortStatus.DEVICE_NOT_CONNECT);
                break;
            }
            case CONNECTED: {
                if (usbDeviceConnection != null) {
                    port.open(baundRate, stop_bits, data_bits, parity);

                    int len = 7;
                    byte[] serialDataBytes = port.getSerialBytes();
                    int value = usbDeviceConnection.controlTransfer( SEND_CTR_REQ_Type, SEND_CTR_REQ, 0, 0, serialDataBytes, len, SEND_CTR_TIMEOUT);

                    //发送未发送完的数据
                    while (value < len && value != 0) {
                        len = len - value;
                        byte[] sendByte = new byte[len];
                        System.arraycopy(serialDataBytes, value, sendByte, 0, len);

                        value = usbDeviceConnection.controlTransfer( SEND_CTR_REQ_Type, SEND_CTR_REQ, 0, 0, sendByte, len, SEND_CTR_TIMEOUT);
                        serialDataBytes = sendByte;
                    }
                } else {
                    port.setStatus(SerialComPort.SerialComPortStatus.DEVICE_NOT_CONNECT);
                }
                break;
            }
            case NO_PERMISSION: {
                port.setStatus(SerialComPort.SerialComPortStatus.DEVICE_NO_PERMISSION);
                break;
            }
            default:{
                port.setStatus(SerialComPort.SerialComPortStatus.NOT_CONNECT);
                break;
            }
        }
        registerMyReceiver();
        return port.status();
    }

    /**
     * close 方法简述
     * <p>关闭串口函数<p/>
     * @param com_id 需要关闭的串口ID
     * @exception NullPointerException com_id 为空时抛出
     */
    public void close(SerialComPort.COM_ID com_id)
    {
        if (com_id == null) throw new NullPointerException("com_id is null");
        serialComPort(com_id).close();

        for ( SerialComPort port : portList)
        {
            if (port.isConnected())
            {
                return;
            }
        }

        closeUsbDeviceConnection();
    }
    /**
     * send 方法简述
     * <p>给串口发送数据</p>
     * @param com_id    指定串口ID
     * @param b         要发送的字节数据
     * @param len       数据字节长度
     * @exception WriteSerialDataException 指定端口com_id 未打开时抛出，必须捕获该异常
     * @exception NullPointerException com_id 为空时抛出
     */
    public void send(SerialComPort.COM_ID com_id, byte[] b, int len ) throws WriteSerialDataException {
        if (com_id == null) throw new NullPointerException("com_id is null");
        serialComPort(com_id).send(b, 0, len);
    }

    /**
     * status 方法简述
     * <p>获取串口的打开状态</p>
     * @param com_id 指定串口ID
     * @return  串口的状态
     * @exception NullPointerException com_id 为空时抛出
     */
    public SerialComPort.SerialComPortStatus status(SerialComPort.COM_ID com_id) throws NullPointerException
    {
        if (com_id == null) throw new NullPointerException("com_id is null");
        return serialComPort(com_id).status();
    }

    /**
     * read 方法简述
     * <p>读取串口发送的数据</p>
     * @param com_id    指定串口ID
     * @param data      接收数组
     * @param length    接收数据大小
     * @param millis    读取超时时间
     * @return          实际读取到的数据字节大小
     * @exception ReadSerialDataException 指定端口com_id 未打开时抛出，必须捕获该异常
     * @exception NullPointerException com_id 为空时抛出
     */
    public int read(SerialComPort.COM_ID com_id, byte[] data, int length, int millis) throws ReadSerialDataException {

        if (com_id == null) throw new NullPointerException("com_id is null");
        int len = serialComPort(com_id).read(data, 0, length);
        if (len > 0){
            return len;
        }
        else
        {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException ignored) {}

            if (serialComPort(com_id).status().equals(SerialComPort.SerialComPortStatus.CONNECTED))
            {
                return serialComPort(com_id).read(data, 0, length);
            }
            else
            {
                return len;
            }

        }

    }


    /**
     * 定时发送数据
     */

    Timer timerSend = null;
    private final static long PERIOD = 100;

    SendTimerTask sendTimerTask =  null;

    private class SendTimerTask extends TimerTask
    {
        @Override
        public void run() {
            // 需要做的事:发送消息
            sendMessageCOM1Handler.obtainMessage(1).sendToTarget();
            sendMessageCOM2Handler.obtainMessage(1).sendToTarget();
            sendMessageCOM3Handler.obtainMessage(1).sendToTarget();
            sendMessageCOM4Handler.obtainMessage(1).sendToTarget();
            sendMessageCOM5Handler.obtainMessage(1).sendToTarget();

        }
    }


    /**
     * 接受广播
     */
    private MyBroadcastReceiver broadCastReceiver = null;
    private class MyBroadcastReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {

            if( UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction()))    //设备被拔出
            {
                Log.d("UsbManager","设备被拔出");
                closeUsbDeviceConnection();
            }

        }
    }

    /**
     * 注册接收广播
     */
    private boolean registerMyReceiver()
    {
        if (broadCastReceiver != null)
        {
            return true;
        }
        else
        {
            broadCastReceiver = new MyBroadcastReceiver();
            try {
                IntentFilter myFilter = new IntentFilter();
                for (String action : listeningActions)
                {
                    myFilter.addAction(action);
                }

                this.context.registerReceiver( broadCastReceiver, myFilter);
                return true;
            }catch (Exception e)
            {
                Log.w("registerReceiver", e.getMessage());
                return false;
            }
        }

    }
    /**
     * 取消接受广播
     */
    private boolean unregisterMyReceiver()
    {
        if (broadCastReceiver != null)
        {
            boolean flag = false;
            try {
                context.unregisterReceiver(broadCastReceiver);
                flag = true;
            }
            catch (Exception e)
            {
                Log.w("unregisterReceiver", e.getMessage());
            }
            finally
            {
                broadCastReceiver = null;
            }
            return flag;
        }
        else
        {
            return true;
        }

    }

    /**
     * 判断是设备是否正确
     * @param device is the Device
     * @return is'nt true
     */
    private boolean isTheDevice(UsbDevice device)
    {
        return device.getVendorId() == deviceVendorId && device.getProductId() == deviceProductId;
    }

    /**
     * 检测USB设备
     * @return is'nt findDevice
     */
    private boolean findUsbDevice()
    {
        if( usbDevice != null && usbDevice.getVendorId() == deviceVendorId
                && usbDevice.getProductId() == deviceProductId)
        {
            return true;
        }
        this.usbManager = (UsbManager) this.context.getSystemService(Context.USB_SERVICE);
        HashMap< String, UsbDevice> deviceList = usbManager.getDeviceList();
        if(deviceList != null)
        {
            for (UsbDevice device : deviceList.values()) {
                if (isTheDevice(device)) {
                    log("Find Device: vid:" + device.getVendorId() +
                            ", pid: " + device.getProductId());

                    usbDevice = device;
                    usbInterface = new UsbInterface[usbDevice.getInterfaceCount()];

                    for (int i = 0; i < usbDevice.getInterfaceCount(); i++) {
                        usbInterface[i] = usbDevice.getInterface(i);
                        for (int j = 0; j < usbInterface[i].getEndpointCount(); j++) {

                            usbEndPoint[i][j] = usbInterface[i].getEndpoint(j);
                            if (usbEndPoint[i][j].getDirection() == 0) {
                                log("Interface " + i + " Endpoint" + j + " FOR INPUT");
                            } else {
                                log("Interface " + i + " Endpoint" + j + " FOR OUTPUT");
                            }
                        }
                    }

                    return true;
                }
            }
        }

        return false;
    }

    private void log(String message)
    {
        Log.d("SerialComPortControl", message);
    }
    private DeviceStatus getDeviceStatus()
    {
        try {
            if( status == DeviceStatus.CONNECTED) return status;

            if( findUsbDevice())
            {
                if(connectDevice())
                {
                    status = DeviceStatus.CONNECTED;
                }
                else
                {
                    status = DeviceStatus.NO_PERMISSION;
                    //请求连接中
                    PendingIntent pi = PendingIntent.getBroadcast(context, 0, new Intent( ACTION_USB_PERMISSION), 0);
                    usbManager.requestPermission(usbDevice, pi);
                }
            }
            else
            {
                log("not Find Device");
                status = DeviceStatus.NOT_CONNECT;
            }
        }
        catch (Exception e)
        {
            log(e.getMessage());
        }
        return  status;
    }

    /**
     * 打开USB设备
     * @return is'nt Open?
     */
    private boolean connectDevice()
    {
        this.usbManager = (UsbManager) this.context.getSystemService(Context.USB_SERVICE);
        if( findUsbDevice() && usbManager.hasPermission(usbDevice)){

            if( usbDeviceConnection != null){
                usbDeviceConnection.close();
            }
//            usbDeviceConnection.
            usbDeviceConnection = usbManager.openDevice( usbDevice);
            if (usbDeviceConnection == null)
            {
                log("not open device");
                status = DeviceStatus.NOT_CONNECT;
                return false;
            }

            usbDeviceConnection.claimInterface(usbInterface[1], true);

            if(readThread != null)
            {
                readThread.stopThread();
            }
            readThread= new ReadUsbMessageThread();
            readThread.start();

            timerSend = new Timer();
            sendTimerTask = new SendTimerTask();
            timerSend.schedule(sendTimerTask, 0, PERIOD);

            status = DeviceStatus.CONNECTED;
            log(DeviceStatus.CONNECTED.toString());
            return true;
        }
        else
        {
            log("not find Device or no Permission");
            status = DeviceStatus.NOT_CONNECT;
            return false;
        }
    }
    private void checkCacheLength()
    {
        byte [] datas = new byte[10];
        usbDeviceConnection.controlTransfer(SEND_CTR_REQ_Type | UsbConstants.USB_DIR_IN, RECV_CTR_LEN_REQ, 0,0,datas,10,SEND_CTR_TIMEOUT);
        for (SerialComPort port: portList)
        {
            int l = datas[port.getPortId().getValue() * 2 - 1] & 0xff;
            int h = datas[port.getPortId().getValue() * 2 - 2] & 0xff;
            port.maxSendLength = ( l << 8) | h;
        }
    }


    private SerialComPort serialComPort(SerialComPort.COM_ID com_id)
    {
        if (portList != null ){

            if(com_id == null)
            {
                Log.e("COMID", "null");
                return null;
            }
            return portList.get(com_id.getValue() - 1);

        }
        else
        {
            Log.d("port list ", "null");
        }

        return null;
    }

    private SerialComPort serialComPort(int com_id)
    {
        return portList.get(com_id - 1);
    }

    /**
     * 发送消息Handler
     */
    class SendMessageHandler extends Handler {

        private SerialComPort.COM_ID portId;

        SendMessageHandler(SerialComPort.COM_ID com_id)
        {
            this.portId = com_id;
        }

        @Override
        public void handleMessage(Message msg) {

            SerialComPort port = serialComPort(portId);

            if (!port.status().equals(SerialComPort.SerialComPortStatus.CONNECTED) || port.getSendBufferSize() == 0)
            {
                return;
            }
            checkCacheLength();

            int maxLen = port.maxSendLength - 63;
            int length = 0;
            while (length < maxLen - 63)
            {
                byte [] data = new byte[64];
                int len = port.getSendData(data,0,60);
                if (len <=0)
                {
                    break;
                }
                byte [] datas = DataTransfer.getDataBuffer( port.getPortId().getValue(), data, len);
                int dataLength = len + 3;
                int value = usbDeviceConnection.bulkTransfer(usbEndPoint[1][0],datas, len + 3,100);
                length += len;

                while (value != dataLength && value != 0)
                {
                    dataLength = dataLength  - value;
                    byte[] sendByte = new byte[dataLength];
                    System.arraycopy( datas,value,sendByte,0,dataLength);

                    value = usbDeviceConnection.bulkTransfer(usbEndPoint[1][0],sendByte, len + 3,100);
                    datas = sendByte;
                }
            }
        }
    }

    /**
     * 接收消息线程
     */
    class ReadUsbMessageThread extends Thread{

        private final int RECV_LENGTH = 64 * 1024;
        private boolean mThreadControl;
        public ReadUsbMessageThread(){
            readDateTransfer = new DataTransfer();
            this.mThreadControl = true;
        }

        public void stopThread()
        {
            this.mThreadControl = false;
        }
        @Override
        public void run() {

            while( this.mThreadControl)
            {
                try {

                    byte[] myBuffer=new byte[RECV_LENGTH];
                    int dataLength;
                    if (usbDeviceConnection == null )
                    {
                        break;
                    }
                    dataLength = usbDeviceConnection.bulkTransfer( usbEndPoint[1][1], myBuffer, RECV_LENGTH, 100);

                    for (int i = 0; i < dataLength; i++) {
                        if( readDateTransfer.AddData(myBuffer[i]))
                        {
                            SerialComPort port = serialComPort(readDateTransfer.getWho());
                            if( port != null)
                            {
                                byte [] datas = readDateTransfer.getDatas();
                                port.putReadData(datas,0,readDateTransfer.getLen());
                                log(port.getPortId() + ":" + new String(datas));
                            }
                            readDateTransfer.reset();
                        }
                    }

                }
                catch (Exception e)
                {
                    log(e.getMessage());
                }
            }
            this.mThreadControl = false;
        }
    }

    private void closeUsbDeviceConnection()
    {
        status = DeviceStatus.NOT_CONNECT;

        if ( sendTimerTask != null)
        {
            sendTimerTask.cancel();
            sendTimerTask = null;
        }

        if (timerSend != null)
        {
            timerSend.cancel();
            timerSend = null;
        }

        if (readThread != null)
        {
            readThread.stopThread();
        }

        if (usbDeviceConnection != null)
        {

            for (UsbInterface anUsbInterface : usbInterface) {
                if (anUsbInterface != null) {
                    usbDeviceConnection.releaseInterface(anUsbInterface);
                }
            }

            usbInterface = null;
            usbDeviceConnection.close();
            usbDeviceConnection = null;
            usbDevice = null;
        }

        //确认所有端口都被关闭
        for (SerialComPort port : portList)
        {
            port.close();
        }

        unregisterMyReceiver();
        log("closeUsbDeviceConnection finished");
    }

}
