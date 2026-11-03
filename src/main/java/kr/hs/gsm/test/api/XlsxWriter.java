package kr.hs.gsm.test.api;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 외부 라이브러리 없이 시트 1개짜리 .xlsx 를 만드는 최소 구현.
 * 첫 행은 굵은 글씨 헤더, 숫자(Number)는 숫자 셀, 나머지는 문자열 셀로 쓴다.
 */
public class XlsxWriter {

	public static void write(OutputStream out, String sheetName, List<String> headers, List<List<Object>> rows) throws IOException {
		ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);

		entry(zip, "[Content_Types].xml",
			  "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
			+ "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
			+ "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
			+ "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
			+ "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
			+ "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
			+ "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
			+ "</Types>");

		entry(zip, "_rels/.rels",
			  "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
			+ "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
			+ "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
			+ "</Relationships>");

		entry(zip, "xl/workbook.xml",
			  "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
			+ "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
			+ "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
			+ "<sheets><sheet name=\"" + escape(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
			+ "</workbook>");

		entry(zip, "xl/_rels/workbook.xml.rels",
			  "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
			+ "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
			+ "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
			+ "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
			+ "</Relationships>");

		//스타일 0 = 기본, 1 = 굵게(헤더)
		entry(zip, "xl/styles.xml",
			  "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
			+ "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
			+ "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"맑은 고딕\"/></font><font><b/><sz val=\"11\"/><name val=\"맑은 고딕\"/></font></fonts>"
			+ "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>"
			+ "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
			+ "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
			+ "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
			+ "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs>"
			+ "</styleSheet>");

		StringBuilder sheet = new StringBuilder();
		sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
			 .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
		sheet.append("<row r=\"1\">");
		for(int c = 0; c < headers.size(); c++) cell(sheet, c, 1, headers.get(c), 1);
		sheet.append("</row>");
		for(int r = 0; r < rows.size(); r++) {
			sheet.append("<row r=\"").append(r + 2).append("\">");
			List<Object> row = rows.get(r);
			for(int c = 0; c < row.size(); c++) cell(sheet, c, r + 2, row.get(c), 0);
			sheet.append("</row>");
		}
		sheet.append("</sheetData></worksheet>");
		entry(zip, "xl/worksheets/sheet1.xml", sheet.toString());

		zip.finish();
	}//write()

	private static void cell(StringBuilder sb, int col, int row, Object value, int style) {
		if(value == null) return;
		String ref = columnName(col) + row;
		if(value instanceof Number) {
			sb.append("<c r=\"").append(ref).append("\" s=\"").append(style).append("\"><v>").append(value).append("</v></c>");
		} else {
			sb.append("<c r=\"").append(ref).append("\" s=\"").append(style).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
			  .append(escape(String.valueOf(value))).append("</t></is></c>");
		}
	}

	//0 → A, 25 → Z, 26 → AA
	private static String columnName(int index) {
		StringBuilder name = new StringBuilder();
		for(int i = index; i >= 0; i = i / 26 - 1) name.insert(0, (char) ('A' + i % 26));
		return name.toString();
	}

	private static String escape(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		for(char ch : s.toCharArray()) {
			switch(ch) {
				case '&': sb.append("&amp;"); break;
				case '<': sb.append("&lt;"); break;
				case '>': sb.append("&gt;"); break;
				case '"': sb.append("&quot;"); break;
				default:
					//XML 에 쓸 수 없는 제어문자 제거 (탭·줄바꿈 제외)
					if(ch >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r') sb.append(ch);
			}
		}
		return sb.toString();
	}

	private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(content.getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}

}//class
