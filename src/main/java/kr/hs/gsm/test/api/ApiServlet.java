package kr.hs.gsm.test.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import kr.hs.gsm.test.UserDTO;

/**
 * 모든 API 서블릿의 공통 부모.
 * - PATCH 메서드 지원 (Servlet 6.0 에는 doPatch 가 없음)
 * - ApiException → {code, message} JSON 오류 응답
 * - 로그인/역할 검사, JSON 읽기·쓰기, 파라미터 변환
 */
public abstract class ApiServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	public static final String SESSION_USER = "loginUser";
	public static final String ROLE_STUDENT = "STUDENT";
	public static final String ROLE_TEACHER = "TEACHER";

	protected static final Gson GSON = new GsonBuilder().serializeNulls().create();

	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		req.setCharacterEncoding("UTF-8");
		try {
			if("PATCH".equalsIgnoreCase(req.getMethod())) {
				doPatch(req, resp);
			} else {
				super.service(req, resp);
			}
		} catch (ApiException e) {
			error(resp, e.getStatus(), e.getCode(), e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
			error(resp, 500, "INTERNAL_ERROR", "서버 오류가 발생했습니다.");
		}
	}//service()

	protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		throw new ApiException(405, "METHOD_NOT_ALLOWED", "지원하지 않는 메서드입니다.");
	}

	//지원하지 않는 메서드는 JSON 405 로 응답
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		throw new ApiException(405, "METHOD_NOT_ALLOWED", "지원하지 않는 메서드입니다.");
	}
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		throw new ApiException(405, "METHOD_NOT_ALLOWED", "지원하지 않는 메서드입니다.");
	}
	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		throw new ApiException(405, "METHOD_NOT_ALLOWED", "지원하지 않는 메서드입니다.");
	}
	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		throw new ApiException(405, "METHOD_NOT_ALLOWED", "지원하지 않는 메서드입니다.");
	}

	// ------------------------------------------------------------------
	// 응답
	// ------------------------------------------------------------------

	protected void json(HttpServletResponse resp, int status, Object body) throws IOException {
		resp.setStatus(status);
		resp.setContentType("application/json; charset=UTF-8");
		resp.getWriter().write(GSON.toJson(body));
	}

	protected void error(HttpServletResponse resp, int status, String code, String message) throws IOException {
		if(resp.isCommitted()) return;
		resp.reset();
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put("code", code);
		body.put("message", message);
		json(resp, status, body);
	}

	//{key: value} 한 쌍짜리 응답 본문
	protected Map<String, Object> result(String key, Object value) {
		Map<String, Object> body = new LinkedHashMap<String, Object>();
		body.put(key, value);
		return body;
	}

	// ------------------------------------------------------------------
	// 인증·권한
	// ------------------------------------------------------------------

	protected UserDTO loginUser(HttpServletRequest req) {
		HttpSession session = req.getSession(false);
		UserDTO user = session == null ? null : (UserDTO) session.getAttribute(SESSION_USER);
		if(user == null) throw ApiException.unauthorized();
		return user;
	}

	protected UserDTO requireTeacher(HttpServletRequest req) {
		UserDTO user = loginUser(req);
		if(!ROLE_TEACHER.equals(user.getRole())) throw ApiException.forbidden("교사만 사용할 수 있습니다.");
		return user;
	}

	protected UserDTO requireStudent(HttpServletRequest req) {
		UserDTO user = loginUser(req);
		if(!ROLE_STUDENT.equals(user.getRole())) throw ApiException.forbidden("학생만 사용할 수 있습니다.");
		return user;
	}

	protected boolean isTeacher(UserDTO user) {
		return ROLE_TEACHER.equals(user.getRole());
	}

	// ------------------------------------------------------------------
	// 요청 경로·본문
	// ------------------------------------------------------------------

	//pathInfo 를 조각으로 분리: "/12/approve" → ["12", "approve"], 없으면 빈 배열
	protected String[] pathParts(HttpServletRequest req) {
		String info = req.getPathInfo();
		if(info == null || info.equals("/") || info.isEmpty()) return new String[0];
		return info.substring(1).split("/");
	}

	protected long parseId(String value) {
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			throw ApiException.notFound("잘못된 경로입니다.");
		}
	}

	protected JsonObject readJson(HttpServletRequest req) throws IOException {
		StringBuilder sb = new StringBuilder();
		try (BufferedReader reader = req.getReader()) {
			String line;
			while((line = reader.readLine()) != null) sb.append(line);
		} catch (java.nio.charset.CharacterCodingException e) {
			throw ApiException.badRequest("INVALID_ENCODING", "요청 본문은 UTF-8 이어야 합니다.");
		}
		if(sb.length() == 0) return new JsonObject();
		try {
			JsonElement el = JsonParser.parseString(sb.toString());
			if(!el.isJsonObject()) throw ApiException.badRequest("INVALID_JSON", "요청 본문은 JSON 객체여야 합니다.");
			return el.getAsJsonObject();
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
			throw ApiException.badRequest("INVALID_JSON", "JSON 형식이 올바르지 않습니다.");
		}
	}

	//JSON 필드 읽기 (없거나 null 이면 null)
	protected String str(JsonObject o, String key) {
		if(o == null || !o.has(key) || o.get(key).isJsonNull()) return null;
		try {
			String value = o.get(key).getAsString().trim();
			return value.isEmpty() ? null : value;
		} catch (Exception e) {
			throw ApiException.badRequest("INVALID_FIELD", key + " 값이 올바르지 않습니다.");
		}
	}

	protected Integer integer(JsonObject o, String key) {
		if(o == null || !o.has(key) || o.get(key).isJsonNull()) return null;
		try {
			return o.get(key).getAsInt();
		} catch (Exception e) {
			throw ApiException.badRequest("INVALID_FIELD", key + " 는 숫자여야 합니다.");
		}
	}

	protected Boolean bool(JsonObject o, String key) {
		if(o == null || !o.has(key) || o.get(key).isJsonNull()) return null;
		try {
			return o.get(key).getAsBoolean();
		} catch (Exception e) {
			throw ApiException.badRequest("INVALID_FIELD", key + " 는 true/false 여야 합니다.");
		}
	}

	protected List<Long> longList(JsonObject o, String key) {
		if(o == null || !o.has(key) || o.get(key).isJsonNull()) return null;
		if(!o.get(key).isJsonArray()) throw ApiException.badRequest("INVALID_FIELD", key + " 는 배열이어야 합니다.");
		JsonArray arr = o.getAsJsonArray(key);
		List<Long> list = new ArrayList<Long>();
		try {
			for(JsonElement el : arr) list.add(el.getAsLong());
		} catch (Exception e) {
			throw ApiException.badRequest("INVALID_FIELD", key + " 에는 숫자 id 만 넣을 수 있습니다.");
		}
		return list;
	}

	protected String required(String value, String key) {
		if(value == null) throw ApiException.badRequest(key.toUpperCase() + "_REQUIRED", key + " 는 필수입니다.");
		return value;
	}

	// ------------------------------------------------------------------
	// 쿼리 파라미터
	// ------------------------------------------------------------------

	protected String strParam(HttpServletRequest req, String name) {
		String value = req.getParameter(name);
		return (value == null || value.isBlank()) ? null : value.trim();
	}

	protected Integer intParam(HttpServletRequest req, String name) {
		String value = strParam(req, name);
		if(value == null) return null;
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw ApiException.badRequest("INVALID_PARAM", name + " 는 숫자여야 합니다.");
		}
	}

	protected Long longParam(HttpServletRequest req, String name) {
		String value = strParam(req, name);
		if(value == null) return null;
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			throw ApiException.badRequest("INVALID_PARAM", name + " 는 숫자여야 합니다.");
		}
	}

	protected Boolean boolParam(HttpServletRequest req, String name) {
		String value = strParam(req, name);
		if(value == null) return null;
		if(value.equalsIgnoreCase("true")) return true;
		if(value.equalsIgnoreCase("false")) return false;
		throw ApiException.badRequest("INVALID_PARAM", name + " 는 true/false 여야 합니다.");
	}

	//YYYY-MM-DD 형식 검사
	protected String date(String value, String name) {
		if(value == null) return null;
		try {
			java.time.LocalDate.parse(value);
			return value;
		} catch (Exception e) {
			throw ApiException.badRequest("INVALID_DATE", name + " 는 YYYY-MM-DD 형식이어야 합니다.");
		}
	}

	// ------------------------------------------------------------------
	// 봉사 검색 조건 (GET /volunteers, bulk-approve filter, 엑셀 출력 공용)
	// ------------------------------------------------------------------

	private static final java.util.Set<String> VOLUNTEER_STATUS =
			java.util.Set.of("DRAFT", "SUBMITTED", "APPROVED", "REJECTED");

	private String volunteerStatus(String status) {
		if(status == null) return null;
		String upper = status.toUpperCase();
		if(!VOLUNTEER_STATUS.contains(upper)) {
			throw ApiException.badRequest("INVALID_STATUS", "status 는 DRAFT / SUBMITTED / APPROVED / REJECTED 중 하나입니다.");
		}
		return upper;
	}

	//쿼리 파라미터 → 검색 조건
	protected kr.hs.gsm.test.VolunteerSearchDTO volunteerSearch(HttpServletRequest req) {
		kr.hs.gsm.test.VolunteerSearchDTO s = new kr.hs.gsm.test.VolunteerSearchDTO();
		s.setGrade(intParam(req, "grade"));
		s.setClassNo(intParam(req, "class"));
		s.setStatus(volunteerStatus(strParam(req, "status")));
		s.setFrom(date(strParam(req, "from"), "from"));
		s.setTo(date(strParam(req, "to"), "to"));
		s.setOrg(strParam(req, "org"));
		s.setMinMinutes(intParam(req, "minMinutes"));
		s.setMaxMinutes(intParam(req, "maxMinutes"));
		s.setHasFile(boolParam(req, "hasFile"));
		return s;
	}

	//JSON filter 객체 → 검색 조건 (키는 쿼리 파라미터와 동일)
	protected kr.hs.gsm.test.VolunteerSearchDTO volunteerSearch(JsonObject f) {
		kr.hs.gsm.test.VolunteerSearchDTO s = new kr.hs.gsm.test.VolunteerSearchDTO();
		s.setGrade(integer(f, "grade"));
		s.setClassNo(integer(f, "class"));
		s.setStatus(volunteerStatus(str(f, "status")));
		s.setFrom(date(str(f, "from"), "from"));
		s.setTo(date(str(f, "to"), "to"));
		s.setOrg(str(f, "org"));
		s.setMinMinutes(integer(f, "minMinutes"));
		s.setMaxMinutes(integer(f, "maxMinutes"));
		s.setHasFile(bool(f, "hasFile"));
		return s;
	}

	//현재 학기 (3~8월 1학기, 9~2월 2학기 / 1~2월은 전년도 2학기) 예: 2026-2
	protected String currentSemester() {
		java.time.LocalDate today = java.time.LocalDate.now();
		int month = today.getMonthValue();
		if(month >= 3 && month <= 8) return today.getYear() + "-1";
		if(month >= 9) return today.getYear() + "-2";
		return (today.getYear() - 1) + "-2";
	}

}//class
