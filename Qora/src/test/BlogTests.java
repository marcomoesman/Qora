package test;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.List;

import ntp.NTP;

import org.json.simple.JSONObject;
import org.junit.Before;
import org.junit.Test;

import api.BlogPostResource;
import qora.account.PrivateKeyAccount;
import qora.crypto.Base58;
import qora.transaction.ArbitraryTransaction;
import qora.transaction.ArbitraryTransactionV1;
import qora.transaction.GenesisTransaction;
import qora.transaction.Transaction;
import database.DBSet;

public class BlogTests {

	private DBSet databaseSet;
	private PrivateKeyAccount creator;

	@Before
	public void setup() {
		// Create in-memory DB
		databaseSet = DBSet.createEmptyDatabaseSet();

		// Create test creator account
		creator = TestUtils.createTestAccount();

		// Process genesis transaction to make sure sender has funds
		Transaction transaction = new GenesisTransaction(creator, BigDecimal.valueOf(1_000_000L).setScale(8), NTP.getTime());
		transaction.process(databaseSet);

	}

	@SuppressWarnings("unchecked")
	private void addToJson(JSONObject jsonObject, String key, String value) {
		if (key == null || value == null)
			return;

		jsonObject.put(key, value);
	}

	private ArbitraryTransactionV1 buildAT(PrivateKeyAccount creator, int service, String author, String blogname, String title, String share, String delete,
			String post, String postid) {
		JSONObject jsonObject = new JSONObject();

		addToJson(jsonObject, BlogPostResource.AUTHOR, author);
		addToJson(jsonObject, BlogPostResource.BLOGNAME_KEY, blogname);
		addToJson(jsonObject, BlogPostResource.TITLE_KEY, title);
		addToJson(jsonObject, BlogPostResource.SHARE_KEY, share);
		addToJson(jsonObject, BlogPostResource.DELETE_KEY, delete);
		addToJson(jsonObject, BlogPostResource.POST_KEY, post);
		addToJson(jsonObject, BlogPostResource.COMMENT_POSTID_KEY, postid);

		byte[] data = jsonObject.toString().getBytes();

		long timestamp = Transaction.getPOWFIX_RELEASE() - 1; // So TransactionFactory.parse() thinks this is V1 AT
		byte[] signature = ArbitraryTransactionV1.generateSignature(databaseSet, creator, service, data, BigDecimal.ONE.setScale(8), timestamp);

		return new ArbitraryTransactionV1(creator, service, data, BigDecimal.ONE.setScale(8), timestamp, creator.getLastReference(databaseSet), signature);
	}

	@Test
	public void testBlogPost() throws Exception {
		ArbitraryTransaction blogPostTx = buildAT(creator, ArbitraryTransaction.SERVICE_BLOG_POST, null, "blogname", null, null, null, "post content", null);

		assertEquals(Transaction.VALIDATE_OK, blogPostTx.isValid());

		blogPostTx.process(databaseSet);

		// Test whether blog post exists
		List<byte[]> blogPostSignatures = databaseSet.getBlogPostMap().get("blogname");

		assertEquals("there should be one blog post", 1, blogPostSignatures.size());
	}

	@Test
	public void testBlogPostOrphan() throws Exception {
		ArbitraryTransaction blogPostTx = buildAT(creator, ArbitraryTransaction.SERVICE_BLOG_POST, null, "blogname", null, null, null, "post content", null);

		assertEquals(Transaction.VALIDATE_OK, blogPostTx.isValid());

		blogPostTx.process(databaseSet);

		// Add blog post transaction to transaction map so it can fetched during orphaning
		databaseSet.getTransactionMap().add(blogPostTx);

		blogPostTx.orphan(databaseSet);

		// Test whether blog post exists
		List<byte[]> blogPostSignatures = databaseSet.getBlogPostMap().get("blogname");

		assertEquals("there should be no blog posts", 0, blogPostSignatures.size());
	}

	@Test
	public void testBlogComment() throws Exception {
		ArbitraryTransaction blogPostTx = buildAT(creator, ArbitraryTransaction.SERVICE_BLOG_POST, null, "blogname", null, null, null, "post content", null);

		blogPostTx.process(databaseSet);

		ArbitraryTransaction blogCommentTx = buildAT(creator, ArbitraryTransaction.SERVICE_BLOG_COMMENT, null, null, null, null, null, "comment content",
				Base58.encode(blogPostTx.getSignature()));

		assertEquals(Transaction.VALIDATE_OK, blogCommentTx.isValid());

		blogCommentTx.process(databaseSet);

		// Test whether blog comment exists
		List<byte[]> blogCommentSignatures = databaseSet.getPostCommentMap().get(blogPostTx.getSignature());

		assertNotNull("blogCommentSignatures list should not be null", blogCommentSignatures);

		assertEquals("there should be one blog comment", 1, blogCommentSignatures.size());
	}

	@Test
	public void testBlogCommentOrphan() throws Exception {
		ArbitraryTransaction blogPostTx = buildAT(creator, ArbitraryTransaction.SERVICE_BLOG_POST, null, "blogname", null, null, null, "post content", null);

		blogPostTx.process(databaseSet);

		// Add blog post transaction to transaction map so it can fetched during orphaning
		databaseSet.getTransactionMap().add(blogPostTx);

		ArbitraryTransaction blogCommentTx = buildAT(creator, ArbitraryTransaction.SERVICE_BLOG_COMMENT, null, null, null, null, null, "comment content",
				Base58.encode(blogPostTx.getSignature()));

		assertEquals(Transaction.VALIDATE_OK, blogCommentTx.isValid());

		blogCommentTx.process(databaseSet);

		// Add blog comment transaction to transaction map so it can fetched during orphaning
		databaseSet.getTransactionMap().add(blogCommentTx);

		blogCommentTx.orphan(databaseSet);

		// Test whether blog comment exists
		List<byte[]> blogCommentSignatures = databaseSet.getPostCommentMap().get(blogPostTx.getSignature());

		if (blogCommentSignatures != null)
			assertEquals("there should be no blog comments", 0, blogCommentSignatures.size());
	}
}
