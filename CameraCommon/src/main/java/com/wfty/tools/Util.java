package com.wfty.tools;



public class Util {


	public static byte[] hexStringToBytes(String hexString) {
		if (hexString == null || hexString.equals("")) {
			return null;
		}
		if (hexString.length() % 2 != 0) {
			hexString = hexString + "0";
		}
		hexString = hexString.toUpperCase();
		int length = hexString.length() / 2;
		char[] hexChars = hexString.toCharArray();
		byte[] d = new byte[length];
		for (int i = 0; i < length; i++) {
			int pos = i * 2;
			d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));

		}
		return d;
	}

	private static byte charToByte(char c) {
		return (byte) "0123456789ABCDEF".indexOf(c);
	}

	public static byte[] shortToBytes(short n) {
		byte[] b = new byte[2];
		b[1] = (byte) (n & 0xff);
		b[0] = (byte) ((n >> 8) & 0xff);
		return b;
	}

	public static short bytesToShort(byte[] b) {
		return (short) (b[1] & 0xff | (b[0] & 0xff) << 8);
	}

	public static String bytesToHexString(byte[] src) {
		StringBuilder stringBuilder = new StringBuilder("");
		if (src == null || src.length <= 0) {
			return null;
		}
		for (int i = 0; i < src.length; i++) {
			int v = src[i] & 0xFF;
			String hv = Integer.toHexString(v);
			if (hv.length() < 2) {
				stringBuilder.append(0);
			}
			stringBuilder.append(hv);
		}
		return stringBuilder.toString();
	}

	public String printHexString(byte[] b) {
		String a = "";
		for (int i = 0; i < b.length; i++) {
			String hex = Integer.toHexString(b[i] & 0xFF);
			if (hex.length() == 1) {
				hex = '0' + hex;
			}

			a = a + hex;
		}

		return a;
	}
	
	
	
	public static byte[] bigtosmall(byte[] a){
		if(a.length>0){
			byte[] b = new byte[a.length];
			for(int i =0;i<a.length;i++){				
				if(i%2==0){
					if((i+1)<a.length)b[i]=a[i+1];
				}else{
					b[i]=a[i-1];
				}
				
			}
			return b;
			
		}		
	return null;
	}
	
	
	/** 
     * ��32λ����ת���ɳ���Ϊ4��byte���� 
     *  
     * @param s 
     *            int 
     * @return byte[] 
     * */  
    public static byte[] intToByteArray(int s) {  
        byte[] targets = new byte[2];  
        for (int i = 0; i < 4; i++) {  
            int offset = (targets.length - 1 - i) * 8;  
            targets[i] = (byte) ((s >>> offset) & 0xff);  
        }  
        return targets;  
    }  
  
    /** 
     * long to byte[] 
     *  
     * @param s 
     *            long 
     * @return byte[] 
     * */  
    public static byte[] longToByteArray(long s) {  
        byte[] targets = new byte[2];  
        for (int i = 0; i < 8; i++) {  
            int offset = (targets.length - 1 - i) * 8;  
            targets[i] = (byte) ((s >>> offset) & 0xff);  
        }  
        return targets;  
    }  
  
    /**32λintתbyte[]*/  
    public static byte[] int2byte(int res) {  
        byte[] targets = new byte[4];  
        targets[0] = (byte) (res & 0xff);// ���λ  
        targets[1] = (byte) ((res >> 8) & 0xff);// �ε�λ  
        targets[2] = (byte) ((res >> 16) & 0xff);// �θ�λ  
        targets[3] = (byte) (res >>> 24);// ���λ,�޷������ơ�  
        return targets;  
    }  
  
    /** 
     * ������Ϊ2��byte����ת��Ϊ16λint 
     *  
     * @param res 
     *            byte[] 
     * @return int 
     * */  
    public static int byte2int(byte[] res) {  
        // res = InversionByte(res);  
        // һ��byte��������24λ���0x??000000��������8λ���0x00??0000  
        int targets = (res[0] & 0xff) | ((res[1] << 8) & 0xff00); // | ��ʾ��λ��  
        return targets;  
    }  
    
    /**32λintתbyte[]*/  
    public static byte[] intTo2byte(int res) {  
        byte[] targets = new byte[2];  
        targets[1] = (byte) (res & 0xff);// ���λ  
        targets[0] = (byte) ((res >> 8) & 0xff);// �ε�λ  
        return targets;  
    }  
}
