package test;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import qora.account.PrivateKeyAccount;

public class TestUtils {
	private static int testAccountCount = 0;
	
	public static PrivateKeyAccount createTestAccount() {
		byte[] seed = Ints.toByteArray(++testAccountCount);
		seed = Bytes.ensureCapacity(seed,  32,  0);
		return new PrivateKeyAccount(seed);
	}
}
