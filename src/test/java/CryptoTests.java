

import java.io.IOException;

import org.junit.Test;

import qora.crypto.RIPEMD160;
import utils.Converter;

public class CryptoTests {

	public static void main(String args[]) throws IOException {
		final int nBytes = System.in.available();
		byte[] input = new byte[nBytes];
		System.in.read(input);

		RIPEMD160 ripEmd160 = new RIPEMD160();
		byte[] output = ripEmd160.digest(input);

		System.out.println(Converter.toHex(output));
	}

	@Test
	public void testRIPEMD160() throws Exception {
		// byte[] input = Converter.parseHexString("0000000000000000000000000000000000000000000000000000000000000000");
		// byte[] input = Converter.parseHexString("4186612a675689c1e20c8078da482576e9e99a3cc0602f88882d5e641eb38785");
		// byte[] input = "The quick brown fox jumps over the lazy cog".getBytes();
		byte[] input = Converter.parseHexString("ff");

		RIPEMD160 ripEmd160 = new RIPEMD160();
		byte[] output = ripEmd160.digest(input);

		System.out.println(Converter.toHex(output));
	}

}
