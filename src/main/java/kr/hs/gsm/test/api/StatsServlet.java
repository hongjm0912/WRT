package kr.hs.gsm.test.api;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.hs.gsm.test.StudentSummaryDTO;
import kr.hs.gsm.test.UserDAO;
import kr.hs.gsm.test.VolunteerDAO;
import kr.hs.gsm.test.VolunteerDTO;
import kr.hs.gsm.test.VolunteerSearchDTO;

/**
 * GET /api/v1/stats?grade&class - 통계 (교사)
 *   응답: 학생 수, 승인 봉사 시간 합계·평균, 상점·벌점 합계, 봉사 상태별 건수, 학생별 요약
 */
@WebServlet("/api/v1/stats")
public class StatsServlet extends ApiServlet {
	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		requireTeacher(req);
		Integer grade = intParam(req, "grade");
		Integer classNo = intParam(req, "class");

		List<StudentSummaryDTO> students = new UserDAO().getSummaryList(grade, classNo);
		int minutes = 0, merit = 0, demerit = 0;
		for(StudentSummaryDTO s : students) {
			minutes += s.getVolunteerMinutes();
			merit += s.getMeritTotal();
			demerit += s.getDemeritTotal();
		}

		VolunteerSearchDTO search = new VolunteerSearchDTO();
		search.setGrade(grade);
		search.setClassNo(classNo);
		Map<String, Integer> statusCounts = new LinkedHashMap<String, Integer>();
		for(String status : new String[] {"DRAFT", "SUBMITTED", "APPROVED", "REJECTED"}) statusCounts.put(status, 0);
		for(VolunteerDTO v : new VolunteerDAO().getVolunteerList(search)) {
			statusCounts.merge(v.getStatus(), 1, Integer::sum);
		}

		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("grade", grade);
		body.put("class", classNo);
		body.put("studentCount", students.size());
		body.put("volunteerMinutesTotal", minutes);
		body.put("volunteerMinutesAvg", students.isEmpty() ? 0 : Math.round(minutes * 10.0 / students.size()) / 10.0);
		body.put("meritTotal", merit);
		body.put("demeritTotal", demerit);
		body.put("volunteerStatusCounts", statusCounts);
		body.put("students", students);
		json(resp, 200, body);
	}//doGet()

}//class
