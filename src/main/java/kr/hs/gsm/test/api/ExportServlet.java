package kr.hs.gsm.test.api;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.hs.gsm.test.PointDAO;
import kr.hs.gsm.test.PointDTO;
import kr.hs.gsm.test.VolunteerDAO;
import kr.hs.gsm.test.VolunteerDTO;

/**
 * 엑셀 출력 API (교사)
 *   GET /api/v1/exports/volunteers.xlsx   필터는 GET /volunteers 와 동일
 *   GET /api/v1/exports/points.xlsx       필터는 GET /points 와 동일 (studentId, semester)
 */
@WebServlet("/api/v1/exports/*")
public class ExportServlet extends ApiServlet {
	private static final long serialVersionUID = 1L;

	private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	private static final java.util.Map<String, String> STATUS_KO = java.util.Map.of(
			"DRAFT", "임시저장", "SUBMITTED", "제출", "APPROVED", "승인", "REJECTED", "반려",
			"ACTIVE", "유효", "CANCELED", "취소", "MERIT", "상점", "DEMERIT", "벌점");

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		requireTeacher(req);
		String[] parts = pathParts(req);
		if(parts.length != 1) throw ApiException.notFound("잘못된 경로입니다.");

		List<String> headers;
		List<List<Object>> rows = new ArrayList<List<Object>>();
		String title;

		switch(parts[0]) {
			case "volunteers.xlsx": {
				title = "봉사기록";
				headers = Arrays.asList("ID", "학년", "반", "번호", "이름", "봉사일", "기관", "시간(분)", "내용", "상태", "반려사유", "증빙", "등록일시");
				for(VolunteerDTO v : new VolunteerDAO().getVolunteerList(volunteerSearch(req))) {
					rows.add(Arrays.<Object>asList(v.getId(), v.getGrade(), v.getClassNo(), v.getStudentNo(), v.getStudentName(),
							v.getVolDate(), v.getOrg(), v.getMinutes(), v.getContent(), ko(v.getStatus()),
							v.getRejectReason(), v.getFileCount(), v.getCreatedAt()));
				}
				break;
			}
			case "points.xlsx": {
				title = "상벌점기록";
				headers = Arrays.asList("ID", "학생", "학기", "구분", "항목", "점수", "메모", "부여교사", "상태", "취소사유", "부여일시");
				for(PointDTO p : new PointDAO().getPointList(longParam(req, "studentId"), strParam(req, "semester"))) {
					rows.add(Arrays.<Object>asList(p.getId(), p.getStudentName(), p.getSemester(), ko(p.getItemType()), p.getItemName(),
							p.getScore(), p.getMemo(), p.getTeacherName(), ko(p.getStatus()), p.getCancelReason(), p.getCreatedAt()));
				}
				break;
			}
			default:
				throw ApiException.notFound("volunteers.xlsx 또는 points.xlsx 만 지원합니다.");
		}

		String fileName = title + "_" + LocalDate.now() + ".xlsx";
		resp.setStatus(200);
		resp.setContentType(XLSX);
		resp.setHeader("Content-Disposition", "attachment; filename=\"" + parts[0] + "\"; filename*=UTF-8''"
				+ URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));
		XlsxWriter.write(resp.getOutputStream(), title, headers, rows);
	}//doGet()

	private String ko(String code) {
		return code == null ? null : STATUS_KO.getOrDefault(code, code);
	}

}//class
