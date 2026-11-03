package kr.hs.gsm.test.api;

import java.io.IOException;
import java.util.List;

import com.google.gson.JsonObject;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.hs.gsm.test.PointDAO;
import kr.hs.gsm.test.PointDTO;
import kr.hs.gsm.test.PointItemDTO;
import kr.hs.gsm.test.UserDTO;

/**
 * 상벌점 기록 API
 *   GET  /api/v1/points?studentId&semester   기록 조회            학생(본인)·교사
 *   POST /api/v1/points                      부여                 교사
 *        { itemId, studentIds: [...], semester?, memo? }  또는  { itemId, filter: {grade, class, q}, semester?, memo? }
 *        semester 를 생략하면 현재 학기
 *   POST /api/v1/points/{id}/cancel          취소 (reason 필수)   부여 교사
 */
@WebServlet(urlPatterns = {"/api/v1/points", "/api/v1/points/*"})
public class PointServlet extends ApiServlet {
	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		if(pathParts(req).length != 0) throw ApiException.notFound("잘못된 경로입니다.");
		UserDTO user = loginUser(req);

		Long studentId = longParam(req, "studentId");
		if(!isTeacher(user)) {
			if(studentId != null && studentId != user.getId()) throw ApiException.forbidden("본인 기록만 조회할 수 있습니다.");
			studentId = user.getId();
		}
		json(resp, 200, new PointDAO().getPointList(studentId, strParam(req, "semester")));
	}//doGet()

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String[] parts = pathParts(req);
		if(parts.length == 0) {
			grant(req, resp);
		} else if(parts.length == 2 && parts[1].equals("cancel")) {
			cancel(req, resp, parseId(parts[0]));
		} else {
			throw ApiException.notFound("잘못된 경로입니다.");
		}
	}//doPost()

	private void grant(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		UserDTO teacher = requireTeacher(req);
		JsonObject body = readJson(req);

		Integer itemId = integer(body, "itemId");
		if(itemId == null) throw ApiException.badRequest("ITEMID_REQUIRED", "itemId 는 필수입니다.");
		PointDAO dao = new PointDAO();
		PointItemDTO item = dao.getPointItemList(false).stream()
				.filter(i -> i.getId() == itemId).findFirst()
				.orElseThrow(() -> ApiException.notFound("상벌점 항목이 없습니다."));
		if(!"Y".equals(item.getIsActive())) throw ApiException.conflict("ITEM_INACTIVE", "사용 중지된 항목은 부여할 수 없습니다.");

		String semester = str(body, "semester");
		if(semester == null) semester = currentSemester();
		if(!semester.matches("\\d{4}-[12]")) throw ApiException.badRequest("INVALID_SEMESTER", "semester 는 2026-2 형식이어야 합니다.");
		String memo = str(body, "memo");

		List<Long> studentIds = longList(body, "studentIds");
		boolean hasFilter = body.has("filter") && body.get("filter").isJsonObject();
		if((studentIds == null) == !hasFilter) {
			throw ApiException.badRequest("IDS_OR_FILTER", "studentIds 배열 또는 filter 객체 중 하나만 보내세요.");
		}

		int granted;
		if(studentIds != null) {
			if(studentIds.isEmpty()) throw ApiException.badRequest("STUDENTIDS_REQUIRED", "부여할 학생을 선택하세요.");
			granted = dao.insertPoints(studentIds, itemId, teacher.getId(), semester, memo);
		} else {
			JsonObject f = body.getAsJsonObject("filter");
			granted = dao.insertPoints(integer(f, "grade"), integer(f, "class"), str(f, "q"),
					itemId, teacher.getId(), semester, memo);
		}
		json(resp, 201, result("granted", granted));
	}//grant()

	private void cancel(HttpServletRequest req, HttpServletResponse resp, long id) throws IOException {
		UserDTO teacher = requireTeacher(req);
		String reason = str(readJson(req), "reason");
		if(reason == null) throw ApiException.badRequest("REASON_REQUIRED", "취소 사유를 입력하세요.");

		PointDAO dao = new PointDAO();
		PointDTO point = dao.getPoint(id);
		if(point == null) throw ApiException.notFound("상벌점 기록이 없습니다.");
		if(point.getTeacherId() != teacher.getId()) throw ApiException.forbidden("부여한 교사만 취소할 수 있습니다.");
		if(!"ACTIVE".equals(point.getStatus())) throw ApiException.conflict("ALREADY_CANCELED", "이미 취소된 기록입니다.");

		if(dao.cancelPoint(id, teacher.getId(), reason) == 0) throw ApiException.conflict("ALREADY_CANCELED", "이미 취소된 기록입니다.");
		json(resp, 200, dao.getPoint(id));
	}//cancel()

}//class
