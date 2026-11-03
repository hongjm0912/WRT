package kr.hs.gsm.test.api;

import java.io.IOException;

import com.google.gson.JsonObject;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.hs.gsm.test.PointDAO;
import kr.hs.gsm.test.PointItemDTO;
import kr.hs.gsm.test.UserDTO;

/**
 * 상벌점 항목 API
 *   GET   /api/v1/point-items          조회 (전체 - 교사는 사용 중지 항목 포함, 학생은 사용 중 항목만)
 *   POST  /api/v1/point-items          등록 { name, type, score, isActive }          교사
 *   PATCH /api/v1/point-items/{id}     수정 (보낸 필드만, id 는 경로 또는 본문)     교사
 */
@WebServlet(urlPatterns = {"/api/v1/point-items", "/api/v1/point-items/*"})
public class PointItemServlet extends ApiServlet {
	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		if(pathParts(req).length != 0) throw ApiException.notFound("잘못된 경로입니다.");
		UserDTO user = loginUser(req);
		json(resp, 200, new PointDAO().getPointItemList(!isTeacher(user)));
	}//doGet()

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		if(pathParts(req).length != 0) throw ApiException.notFound("잘못된 경로입니다.");
		requireTeacher(req);
		JsonObject body = readJson(req);

		PointItemDTO dto = new PointItemDTO();
		dto.setName(required(str(body, "name"), "name"));
		dto.setType(validType(required(str(body, "type"), "type")));
		dto.setScore(validScore(integer(body, "score")));
		dto.setIsActive(validActive(body));

		PointDAO dao = new PointDAO();
		long id = dao.insertPointItem(dto);
		if(id == 0) throw new ApiException(500, "SAVE_FAILED", "항목을 저장하지 못했습니다.");
		dto.setId(id);
		json(resp, 201, dto);
	}//doPost()

	@Override
	protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		requireTeacher(req);
		String[] parts = pathParts(req);
		JsonObject body = readJson(req);

		Long id;
		if(parts.length == 1) {
			id = parseId(parts[0]);
		} else if(parts.length == 0) {
			Integer bodyId = integer(body, "id");
			if(bodyId == null) throw ApiException.badRequest("ID_REQUIRED", "수정할 항목 id 를 경로나 본문에 넣어 주세요.");
			id = bodyId.longValue();
		} else {
			throw ApiException.notFound("잘못된 경로입니다.");
		}

		final long targetId = id;
		PointDAO dao = new PointDAO();
		PointItemDTO item = dao.getPointItemList(false).stream()
				.filter(i -> i.getId() == targetId).findFirst()
				.orElseThrow(() -> ApiException.notFound("상벌점 항목이 없습니다."));

		if(body.has("name")) item.setName(required(str(body, "name"), "name"));
		if(body.has("type")) item.setType(validType(required(str(body, "type"), "type")));
		if(body.has("score")) item.setScore(validScore(integer(body, "score")));
		if(body.has("isActive")) item.setIsActive(validActive(body));

		if(dao.updatePointItem(item) == 0) throw new ApiException(500, "SAVE_FAILED", "항목을 수정하지 못했습니다.");
		json(resp, 200, item);
	}//doPatch()

	private String validType(String type) {
		String upper = type.toUpperCase();
		if(!upper.equals("MERIT") && !upper.equals("DEMERIT")) {
			throw ApiException.badRequest("INVALID_TYPE", "type 은 MERIT(상점) 또는 DEMERIT(벌점)입니다.");
		}
		return upper;
	}

	private int validScore(Integer score) {
		if(score == null) throw ApiException.badRequest("SCORE_REQUIRED", "score 는 필수입니다.");
		if(score <= 0 || score > 999) throw ApiException.badRequest("INVALID_SCORE", "score 는 1~999 사이여야 합니다.");
		return score;
	}

	//isActive: true/false 를 Y/N 으로 (없으면 Y)
	private String validActive(JsonObject body) {
		Boolean active = bool(body, "isActive");
		return (active == null || active) ? "Y" : "N";
	}

}//class
