package kr.hs.gsm.test.api;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.hs.gsm.test.StudentSummaryDTO;
import kr.hs.gsm.test.UserDAO;
import kr.hs.gsm.test.UserDTO;

/**
 * GET /api/v1/me - 내 정보, 역할, 상점·벌점 합계 (권한: 전체 - 로그인 필요)
 *   교사는 합계가 null
 */
@WebServlet("/api/v1/me")
public class MeServlet extends ApiServlet {
	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		UserDTO login = loginUser(req);
		UserDAO dao = new UserDAO();
		UserDTO user = dao.getUser(login.getId());
		if(user == null) throw ApiException.unauthorized();

		StudentSummaryDTO summary = ROLE_STUDENT.equals(user.getRole()) ? dao.getStudentSummary(user.getId()) : null;

		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("id", user.getId());
		body.put("name", user.getName());
		body.put("email", user.getEmail());
		body.put("role", user.getRole());
		body.put("grade", user.getGrade());
		body.put("class", user.getClassNo());
		body.put("number", user.getStudentNo());
		body.put("meritTotal", summary == null ? null : summary.getMeritTotal());
		body.put("demeritTotal", summary == null ? null : summary.getDemeritTotal());
		json(resp, 200, body);
	}//doGet()

}//class
