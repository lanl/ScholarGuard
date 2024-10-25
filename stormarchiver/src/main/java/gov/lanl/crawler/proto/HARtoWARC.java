package gov.lanl.crawler.proto;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

//import org.archive.io.warc.WARCWriter;
//import org.jwat.warc.WarcDigest;
//import org.jwat.warc.WarcWriter;

import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.model.HarContent;
import de.sstoehr.harreader.model.HarEntry;
import de.sstoehr.harreader.model.HarHeader;
import de.sstoehr.harreader.model.HarPostData;
import de.sstoehr.harreader.model.HarQueryParam;
import de.sstoehr.harreader.model.HarRequest;
import de.sstoehr.harreader.model.HarResponse;
import java.util.regex.Pattern;

import org.archive.io.warc.WARCRecordInfo;
import org.archive.io.warc.WARCWriter;
import org.archive.io.warc.WARCWriterPoolSettings;
import org.archive.uid.RecordIDGenerator;
import org.archive.uid.UUIDGenerator;
import org.archive.util.ArchiveUtils;
import org.archive.util.anvl.ANVLRecord;
import org.archive.format.warc.WARCConstants.WARCRecordType;
import org.archive.io.WriterPoolSettings;
import org.archive.io.arc.ARCConstants;
import org.apache.commons.io.IOUtils;
import org.archive.format.warc.WARCConstants;

//import org.apache.commons.codec.digest.DigestUtils;
public class HARtoWARC {
	String harsdir = "/Users/Lyudmila/Dropbox/DOI_experiment/doi_crawls/httpsdoicrawl/";

//WARC record types, cf.
// http://iipc.github.io/warc-specifications/specifications/warc-format/warc-1.1/#warc-record-types
	/** WARC record type to hold a HTTP request */
	protected static final String WARC_TYPE_REQUEST = "request";
	/** WARC record type to hold a HTTP response */
	protected static final String WARC_TYPE_RESPONSE = "response";
	/**
	 * WARC record type to hold any other resource, including a HTTP response with
	 * no HTTP headers available
	 */
	protected static final String WARC_TYPE_RESOURCE = "resource";
	protected static final String WARC_TYPE_WARCINFO = "warcinfo";

	protected static final String WARC_VERSION = "WARC/1.0";
	protected static final String CRLF = "\r\n";
	protected static final byte[] CRLF_BYTES = { 13, 10 };
	// private static final String digestNoContent = getDigestSha1(new byte[0]);
	protected static final Pattern PROBLEMATIC_HEADERS = Pattern
			.compile("(?i)(?:Content-(?:Encoding|Length)|Transfer-Encoding)");

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		HARtoWARC hv = new HARtoWARC();
		File target = new File("./test.warc");
		File harfile= new File(hv.harsdir + "10.111712.328670" + ".har");
		hv.hartowarc(harfile, target);
		

	}

	public String getHTTPversion(HarResponse req) {
		String version = req.getHttpVersion();
		if (version != null) {
			if (version.toUpperCase().equals("HTTP/1.1")) {
				return version;
			}
			if (version.toUpperCase().equals("HTTP/1.0")) {
				return version;
			}
		}
		return "HTTP/1.1";
	}

	public WARCWriter init_warc_writer(File target, String id) {

		WARCWriter writer = null;
		try {
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(target));

			// FileInputStream is = new FileInputStream(fieldsSrc);
			// ANVLRecord ar;

			// ar = ANVLRecord.load(is);

			// List<String> metadata = new ArrayList<String>(1);
			// metadata.add(ar.toString());
			List<String> metadata = getMetadata();

			writer = new WARCWriter(new AtomicInteger(), bos, target, getSettings(false, null, null, metadata));
			// Write a warcinfo record with description about how this WARC
			// was made.
			writer.writeWarcinfoRecord(target.getName(), "Made from " + id + " by " + this.getClass().getName());
			return writer;

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private WARCWriterPoolSettings getSettings(final boolean isCompressed, final String prefix,
			final List<File> arcDirs, final List metadata) {
		return new WARCWriterPoolSettings() {
			public List<File> calcOutputDirs() {
				return arcDirs;
			}

			@SuppressWarnings({ "unchecked", "rawtypes" })
			public List getMetadata() {
				return metadata;
			}

			public String getPrefix() {
				return prefix;
			}

			public boolean getCompress() {
				return isCompressed;
			}

			public long getMaxFileSizeBytes() {
				return ARCConstants.DEFAULT_MAX_ARC_FILE_SIZE;
			}

			public String getTemplate() {
				return "${prefix}-${timestamp17}-${serialno}";
			}

			public boolean getFrequentFlushes() {
				return false;
			}

			public int getWriteBufferSize() {
				return 4096;
			}

			public RecordIDGenerator getRecordIDGenerator() {
				return new UUIDGenerator();
			}
		};
	}

	public void parseRequest(HarRequest req, WARCWriter wr) {
		String urstr = req.getUrl();

		List<HarQueryParam> query = req.getQueryString();

		String path = "";
		String host = "";

		try {
			URL url = new URL(urstr);
			path = url.getPath();
			host = url.getHost();
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (query.size() > 0) {
			path = path + '?';

			Iterator<HarQueryParam> it = query.iterator();
			// url encode ?
			while (it.hasNext()) {
				HarQueryParam q = it.next();
				String name = q.getName();
				String value = q.getValue();

				path = path + name + "=" + value + "&";

			}
			path = path.substring(0, path.length() - 1);
		}

		String status_line = req.getMethod().name() + ' ' + path + ' ' + req.getHttpVersion() + "\r\n";
		System.out.print(status_line);

		List<HarHeader> reqheaders = req.getHeaders();
		Iterator<HarHeader> ith = reqheaders.iterator();
		StringBuffer sb = new StringBuffer();
		while (ith.hasNext()) {
			HarHeader h = ith.next();
			String name = h.getName();
			String v = h.getValue();
			// if (PROBLEMATIC_HEADERS.matcher(name).matches()) {
//
//			} else {
			sb.append(name + ": " + v + "\r\n");
//			}

		}
		sb.append(CRLF);
		sb.append(CRLF);
		String headers = sb.toString() + "\r\n";
		// System.out.println("headers");
		// System.out.println(headers);
		Long s = req.getBodySize();

		if (s > 0) {
			HarPostData p = req.getPostData();
			String rp = p.getMimeType();

			System.out.println("rp:" + rp);
			String hp = p.getText();
			sb.append(hp);
		}

		// end =ArchiveUtils.getLog14Date(date_untill);

		byte[] reqload = sb.toString().getBytes(StandardCharsets.UTF_8);
		long siz = reqload.length;

		InputStream ris = new ByteArrayInputStream(reqload);
		String mainID = UUID.randomUUID().toString();
		WARCRecordInfo ri = new WARCRecordInfo();
		ri.setContentLength(siz);
		try {
			ri.setRecordId(new URI("urn:uuid:" + UUID.randomUUID().toString()));
		} catch (URISyntaxException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		ri.setUrl(urstr);
		ri.setType(WARCRecordType.request);
		ri.setMimetype(WARCConstants.HTTP_REQUEST_MIMETYPE);
		ri.setContentStream(ris);
		try {
			wr.writeRecord(ri);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public String getValueOrDefault(String value, String defaultValue) {
		return isNotNullOrEmpty(value) ? value : defaultValue;
	}

	private static boolean isNotNullOrEmpty(String str) {
		return str != null && !str.isEmpty();
	}

	void parseResponse(HarResponse req, WARCWriter wr, Date d, String url) {
		List<HarHeader> reqheaders = req.getHeaders();
		// Map<String, Object> map = req.getAdditional();
		// map.forEach((key, value) -> System.out.println(key + ":" + value));

		HarContent body = req.getContent();
		StringBuffer sb = new StringBuffer();
		if (reqheaders == null || body == null) {
			System.out.println("headers empty or body");
			sb.append("Content-Length : 0" + "\r\n");
		}

		Integer status = req.getStatus();
		if (status == 0)
			status = 204;
		System.out.println("status_" + status);
		String status_str = getValueOrDefault(String.valueOf(status), "204");
		String reason = req.getStatusText();
		byte[] content = null;
		if (reason == null) {
			reason = "No Reason";
		}

		String status_line = getHTTPversion(req) + " " + status_str + " " + reason + "\r\n";
		System.out.println("status" + status_line);

		Long s = req.getBodySize();
		String mimetype = "";
		long contentLength = 0;

		if (s > 0) {
			String en = body.getEncoding();
			mimetype = body.getMimeType();
			String b = body.getText();

			if (en != null) {
				if (en.equals("base64")) {
					content = Base64.getDecoder().decode(b);

				}
			} else {
				content = b.getBytes(StandardCharsets.UTF_8);
				System.out.print(b);
			}
			contentLength = content.length;
		}

		Iterator<HarHeader> ith = reqheaders.iterator();

		while (ith.hasNext()) {
			HarHeader h = ith.next();
			String name = h.getName();
			String v = h.getValue();
			if (PROBLEMATIC_HEADERS.matcher(name).matches()) {
				// System.out.println ("name:"+name);
				// System.out.println (v);
			} else {
				sb.append(name + ": " + v + "\r\n");
			}

			if (name.equalsIgnoreCase("content-length")) {
				// add effective uncompressed and unchunked length of
				// content

				// sb.append(name + ": " + contentLength + "\r\n");
				sb.append("Content-Length").append(": ").append(contentLength).append(CRLF);

			}

		}
		// String headers = sb.toString()+ "\r\n";
		sb.append(CRLF);
		sb.append(CRLF);
		byte[] httpheaders = (status_line + sb.toString()).getBytes(StandardCharsets.UTF_8);

		int capacity = httpheaders.length;
		if (content != null) {
			capacity += content.length;
		}
		// String headers_ = sb.toString();
		// System.out.println("headers:"+headers_);
		ByteBuffer bytebuffer = ByteBuffer.allocate(capacity);
		bytebuffer.put(httpheaders);
		// the binary content itself
		if (content != null) {
			bytebuffer.put(content);
		}

		/*
		 * //later String payloadDigest = digestNoContent; String blockDigest; if
		 * (content != null) { contentLength = content.length; payloadDigest =
		 * getDigestSha1(content); blockDigest = getDigestSha1(httpheaders, content); }
		 * else { blockDigest = getDigestSha1(httpheaders); }
		 * 
		 */

		byte[] resp = bytebuffer.array();
		long siz = resp.length;
		InputStream ris = new ByteArrayInputStream(resp);
		WARCRecordInfo ri = new WARCRecordInfo();
		// can add some timeinfo from har and sessionid?
		// ri.addExtraHeader("WARC-Record-ID", value);
		ri.setType(WARCRecordType.response);
		ri.setMimetype(WARCConstants.HTTP_RESPONSE_MIMETYPE);

		ri.setUrl(url);
		ri.setContentLength(siz);
		ri.setContentStream(ris);

		String dt = ArchiveUtils.getLog14Date(d);
		ri.setCreate14DigitDate(dt);

		try {
			ri.setRecordId(new URI("urn:uuid:" + UUID.randomUUID().toString()));
		} catch (URISyntaxException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		try {
			wr.writeRecord(ri);
		}

		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			IOUtils.closeQuietly(ris);
		}

	}

	public void hartowarc(File harfname, File target) {
        String fname=harfname.getName();
		WARCWriter wr = init_warc_writer(target, fname);

		HarReader harReader = new HarReader();
		de.sstoehr.harreader.model.Har harr = null;
		try {
			harr = harReader.readFromFile(harfname);
		} catch (HarReaderException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		List<HarEntry> entries = harr.getLog().getEntries();
		for (HarEntry entry : entries) {
			HarRequest req = entry.getRequest();
			String url = req.getUrl();
			parseRequest(req, wr);

			Date warc_date = entry.getStartedDateTime();
			HarResponse res = entry.getResponse();
			entry.getServerIPAddress();
			entry.getPageref();

			parseResponse(res, wr, warc_date, url);

		}

		try {
			wr.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public List getMetadata() {
		List<String> list = new ArrayList();
		list.add("format:WARC File Format 1.0\n");
		list.add("conformsTo:http://bibnum.bnf.fr/WARC/WARC_ISO_28500_version1_latestdraft.pdf\n");
		list.add("operator:capture-achive\n");
		return list;
	}

}
