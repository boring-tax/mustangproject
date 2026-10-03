package org.mustangproject.validator;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import static org.xmlunit.assertj.XmlAssert.assertThat;

public class ValidatorReuseAndReportTest extends ResourceCase {

	private static final String STATUS = "/validation/summary/@status";

	private byte[] read(String resource) throws IOException {
		return Files.readAllBytes(getResourceAsFile(resource).toPath());
	}

	/**
	 * A validator reused for several files (as the CLI does for directories) kept format, PDF flag and profile of the
	 * previous file: after a UBL file, the EN16931 and XRechnung schematrons were skipped for CII files.
	 */
	@Test
	public void testReusedValidatorDoesNotKeepStateOfPreviousFile() throws IOException {
		ZUGFeRDValidator fresh = new ZUGFeRDValidator();
		assertThat(fresh.validate(read("invalidXRV30.xml"), "invalidXRV30.xml")).valueByXPath(STATUS).isEqualTo("invalid");

		ZUGFeRDValidator reused = new ZUGFeRDValidator();
		assertThat(reused.validate(read("EN16931_Einfach.ubl.xml"), "EN16931_Einfach.ubl.xml")).valueByXPath(STATUS).isEqualTo("valid");
		assertThat(reused.validate(read("invalidXRV30.xml"), "invalidXRV30.xml")).valueByXPath(STATUS).isEqualTo("invalid");

		// a PDF followed by a UBL file must not complain that a PDF may not contain UBL
		reused.validate(read("validXRechnung.pdf"), "validXRechnung.pdf");
		String ubl = reused.validate(read("EN16931_Einfach.ubl.xml"), "EN16931_Einfach.ubl.xml");
		assertThat(ubl).valueByXPath(STATUS).isEqualTo("valid");
	}

	/**
	 * The filename was written into the report unescaped, so a name like "R&amp;D.xml" made the report unparseable
	 * and the validator returned an empty report.
	 */
	@Test
	public void testFilenameIsEscapedInReport() throws IOException {
		String filename = "R&D <\"O'Brien\">.xml";
		String res = new ZUGFeRDValidator().validate(read("validXRV30.xml"), filename);

		assertThat(res).valueByXPath("/validation/@filename").isEqualTo(filename);
		assertThat(res).valueByXPath(STATUS).isEqualTo("valid");
	}

	/**
	 * available() was taken as the length of the stream, streams reporting 0 were rejected as "File too small".
	 */
	@Test
	public void testValidateStreamWithoutAvailableBytes() throws IOException {
		InputStream noAvailable = new FilterInputStream(new ByteArrayInputStream(read("validXRV30.xml"))) {
			@Override
			public int available() {
				return 0;
			}
		};

		String res = new ZUGFeRDValidator().validate(noAvailable, "validXRV30.xml");

		assertThat(res).valueByXPath(STATUS).isEqualTo("valid");
	}
}
