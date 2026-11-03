package kr.hs.gsm.test.api;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.hs.gsm.test.StudentSummaryDTO;
import kr.hs.gsm.test.UserDAO;
import kr.hs.gsm.test.UserDTO;

/**
 * 학생 API
 *   GET /api/v1/students?grade&class&q      학생 목록 (일괄 부여 대상 필터)   교사
 *   GET /api/v1/students/{id}/summary       봉사 누적·상점·벌점 합계          본인·교사
 */
@WebServlet(urlPatterns = {"/api/v1/students", "/api/v1/students/*"})
public class StudentServlet extends ApiServlet {
	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String[] parts = pathParts(req);
		UserDAO dao = new UserDAO();

		if(parts.length == 0) {
			requireTeacher(req);
			json(resp, 200, dao.getStudentList(intParam(req, "grade"), intParam(req, "class"), strParam(req, "q")));

		} else if(parts.length == 2 && parts[1].equals("summary")) {
			UserDTO user = loginUser(req);
			long id = parseId(parts[0]);
			if(!isTeacher(user) && user.getId() != id) throw ApiException.forbidden("본인 요약만 조회할 수 있습니다.");

			UserDTO student = dao.getUser(id);
			if(student == null || !ROLE_STUDENT.equals(student.getRole())) throw ApiException.notFound("학생이 없습니다.");
			StudentSummaryDTO summary = dao.getStudentSummary(id);
			json(resp, 200, summary);

		} else {
			throw ApiException.notFound("잘못된 경로입니다.");
		}
	}//doGet()

}//class
