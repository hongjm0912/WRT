package kr.hs.gsm.test.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.google.gson.JsonObject;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import kr.hs.gsm.test.UserDTO;
import kr.hs.gsm.test.VolunteerDAO;
import kr.hs.gsm.test.VolunteerDTO;
import kr.hs.gsm.test.VolunteerFileDTO;
import kr.hs.gsm.test.VolunteerSearchDTO;

/**
 * 봉사 기록 API
 *   GET   /api/v1/volunteers                    목록(필터)        학생(본인)·교사
 *   POST  /api/v1/volunteers                    등록              학생
 *   PATCH /api/v1/volunteers/{id}               임시저장 건 수정  학생(본인, 제출 전)
 *   POST  /api/v1/volunteers/{id}/files         증빙 업로드       학생(본인)
 *   POST  /api/v1/volunteers/{id}/approve       승인              교사
 *   POST  /api/v1/volunteers/{id}/reject        반려(reason 필수) 교사
 *   POST  /api/v1/volunteers/bulk-approve       일괄 승인         교사
 */
@WebServlet(urlPatterns = {"/api/v1/volunteers", "/api/v1/volunteers/*"})
@MultipartConfig(maxFileSize = 10 * 1024 * 1024, maxRequestSize = 20 * 1024 * 1024)
public class VolunteerServlet extends ApiServlet {
	private static final long serialVersionUID = 1L;

	private static final Set<String> ALLOWED_EXT = Set.of("pdf", "jpg", "jpeg", "png");

	// ------------------------------------------------------------------
	// GET
	// ------------------------------------------------------------------

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		if(pathParts(req).length != 0) throw ApiException.notFound("잘못된 경로입니다.");
		UserDTO user = loginUser(req);

		VolunteerSearchDTO search = volunteerSearch(req);
		if(!isTeacher(user)) search.setStudentId(user.getId());	//학생은 본인 것만

		json(resp, 200, new VolunteerDAO().getVolunteerList(search));
	}//doGet()

	// ------------------------------------------------------------------
	// POST
	// ------------------------------------------------------------------

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String[] parts = pathParts(req);
		if(parts.length == 0) {
			create(req, resp);
		} else if(parts.length == 1 && parts[0].equals("bulk-approve")) {
			bulkApprove(req, resp);
		} else if(parts.length == 2) {
			long id = parseId(parts[0]);
			switch(parts[1]) {
				case "files":   uploadFile(req, resp, id); break;
				case "approve": approve(req, resp, id); break;
				case "reject":  reject(req, resp, id); break;
				default: throw ApiException.notFound("잘못된 경로입니다.");
			}
		} else {
			throw ApiException.notFound("잘못된 경로입니다.");
		}
	}//doPost()

	//POST /volunteers - { org, date, minutes, content, submit }
	private void create(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		UserDTO user = requireStudent(req);
		JsonObject body = readJson(req);

		VolunteerDTO dto = new VolunteerDTO();
		dto.setStudentId(user.getId());
		dto.setOrg(required(str(body, "org"), "org"));
		dto.setVolDate(date(required(str(body, "date"), "date"), "date"));
		dto.setMinutes(validMinutes(integer(body, "minutes")));
		dto.setContent(str(body, "content"));
		dto.setStatus(Boolean.TRUE.equals(bool(body, "submit")) ? "SUBMITTED" : "DRAFT");

		long id = new VolunteerDAO().insertVolunteer(dto);
		if(id == 0) throw new ApiException(500, "SAVE_FAILED", "봉사 기록을 저장하지 못했습니다.");
		json(resp, 201, new VolunteerDAO().getVolunteer(id));
	}//create()

	//POST /volunteers/{id}/files - multipart, 필드명 file
	private void uploadFile(HttpServletRequest req, HttpServletResponse resp, long id) throws IOException, ServletException {
		UserDTO user = requireStudent(req);
		VolunteerDTO v = findOwned(id, user);
		if(!"DRAFT".equals(v.getStatus()) && !"SUBMITTED".equals(v.getStatus())) {
			throw ApiException.conflict("ALREADY_REVIEWED", "승인·반려된 기록에는 증빙을 추가할 수 없습니다.");
		}

		String contentType = req.getContentType();
		if(contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data")) {
			throw ApiException.badRequest("MULTIPART_REQUIRED", "multipart/form-data 로 업로드하세요.");
		}
		Part part;
		try {
			part = req.getPart("file");
		} catch (IllegalStateException e) {
			throw new ApiException(413, "FILE_TOO_LARGE", "파일은 10MB 이하만 올릴 수 있습니다.");
		}
		if(part == null || part.getSize() == 0) throw ApiException.badRequest("FILE_REQUIRED", "file 필드에 파일을 넣어 주세요.");

		//파일명 정리: 경로 제거 + 위험 문자 치환
		String original = part.getSubmittedFileName() == null ? "file" : Paths.get(part.getSubmittedFileName()).getFileName().toString();
		String safeName = original.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
		int dot = safeName.lastIndexOf('.');
		String ext = dot < 0 ? "" : safeName.substring(dot + 1).toLowerCase(Locale.ROOT);
		if(!ALLOWED_EXT.contains(ext)) throw ApiException.badRequest("INVALID_FILE_TYPE", "pdf, jpg, jpeg, png 파일만 올릴 수 있습니다.");

		Path baseDir = uploadDir();
		String relative = "volunteers/" + id + "/" + System.currentTimeMillis() + "_" + safeName;
		Path target = baseDir.resolve(relative).normalize();
		if(!target.startsWith(baseDir)) throw ApiException.badRequest("INVALID_FILE_NAME", "파일명이 올바르지 않습니다.");
		Files.createDirectories(target.getParent());
		try (InputStream in = part.getInputStream()) {
			Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
		}

		VolunteerFileDTO file = new VolunteerFileDTO();
		file.setVolunteerId(id);
		file.setFileName(original);
		file.setFilePath(relative);
		long fileId = new VolunteerDAO().insertFile(file);
		if(fileId == 0) {
			Files.deleteIfExists(target);
			throw new ApiException(500, "SAVE_FAILED", "파일 정보를 저장하지 못했습니다.");
		}
		file.setId(fileId);
		json(resp, 201, file);
	}//uploadFile()

	//POST /volunteers/{id}/approve
	private void approve(HttpServletRequest req, HttpServletResponse resp, long id) throws IOException {
		UserDTO teacher = requireTeacher(req);
		VolunteerDTO v = findSubmitted(id);
		if(new VolunteerDAO().approveVolunteer(v.getId(), teacher.getId()) == 0) {
			throw ApiException.conflict("NOT_SUBMITTED", "제출된 기록만 승인할 수 있습니다.");
		}
		json(resp, 200, new VolunteerDAO().getVolunteer(id));
	}//approve()

	//POST /volunteers/{id}/reject - { reason }
	private void reject(HttpServletRequest req, HttpServletResponse resp, long id) throws IOException {
		UserDTO teacher = requireTeacher(req);
		String reason = str(readJson(req), "reason");
		if(reason == null) throw ApiException.badRequest("REASON_REQUIRED", "반려 사유를 입력하세요.");
		VolunteerDTO v = findSubmitted(id);
		if(new VolunteerDAO().rejectVolunteer(v.getId(), teacher.getId(), reason) == 0) {
			throw ApiException.conflict("NOT_SUBMITTED", "제출된 기록만 반려할 수 있습니다.");
		}
		json(resp, 200, new VolunteerDAO().getVolunteer(id));
	}//reject()

	//POST /volunteers/bulk-approve - { ids: [...] } 또는 { filter: {...} }
	private void bulkApprove(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		UserDTO teacher = requireTeacher(req);
		JsonObject body = readJson(req);
		List<Long> ids = longList(body, "ids");
		boolean hasFilter = body.has("filter") && body.get("filter").isJsonObject();

		if((ids == null) == !hasFilter) {
			throw ApiException.badRequest("IDS_OR_FILTER", "ids 배열 또는 filter 객체 중 하나만 보내세요.");
		}
		int approved = ids != null
				? new VolunteerDAO().bulkApprove(ids, teacher.getId())
				: new VolunteerDAO().bulkApprove(volunteerSearch(body.getAsJsonObject("filter")), teacher.getId());
		json(resp, 200, result("approved", approved));
	}//bulkApprove()

	// ------------------------------------------------------------------
	// PATCH
	// ------------------------------------------------------------------

	//PATCH /volunteers/{id} - 보낸 필드만 수정, submit: true 면 제출
	@Override
	protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String[] parts = pathParts(req);
		if(parts.length != 1) throw ApiException.notFound("잘못된 경로입니다.");
		UserDTO user = requireStudent(req);
		VolunteerDTO v = findOwned(parseId(parts[0]), user);
		if(!"DRAFT".equals(v.getStatus())) throw ApiException.conflict("ALREADY_SUBMITTED", "제출된 기록은 수정할 수 없습니다.");

		JsonObject body = readJson(req);
		if(body.has("org")) v.setOrg(required(str(body, "org"), "org"));
		if(body.has("date")) v.setVolDate(date(required(str(body, "date"), "date"), "date"));
		if(body.has("minutes")) v.setMinutes(validMinutes(integer(body, "minutes")));
		if(body.has("content")) v.setContent(str(body, "content"));
		v.setStatus(Boolean.TRUE.equals(bool(body, "submit")) ? "SUBMITTED" : "DRAFT");

		if(new VolunteerDAO().updateVolunteer(v) == 0) {
			throw ApiException.conflict("ALREADY_SUBMITTED", "제출된 기록은 수정할 수 없습니다.");
		}
		json(resp, 200, new VolunteerDAO().getVolunteer(v.getId()));
	}//doPatch()

	// ------------------------------------------------------------------
	// 공통
	// ------------------------------------------------------------------

	private int validMinutes(Integer minutes) {
		if(minutes == null) throw ApiException.badRequest("MINUTES_REQUIRED", "minutes 는 필수입니다.");
		if(minutes <= 0) throw ApiException.badRequest("INVALID_MINUTES", "minutes 는 1 이상이어야 합니다.");
		return minutes;
	}

	private VolunteerDTO find(long id) {
		VolunteerDTO v = new VolunteerDAO().getVolunteer(id);
		if(v == null) throw ApiException.notFound("봉사 기록이 없습니다.");
		return v;
	}

	private VolunteerDTO findOwned(long id, UserDTO user) {
		VolunteerDTO v = find(id);
		if(v.getStudentId() != user.getId()) throw ApiException.forbidden("본인 기록만 다룰 수 있습니다.");
		return v;
	}

	private VolunteerDTO findSubmitted(long id) {
		VolunteerDTO v = find(id);
		if(!"SUBMITTED".equals(v.getStatus())) throw ApiException.conflict("NOT_SUBMITTED", "제출된 기록만 처리할 수 있습니다.");
		return v;
	}

	//업로드 폴더: web.xml 의 UPLOAD_DIR, 없으면 {사용자 홈}/wrt-upload
	private Path uploadDir() throws IOException {
		String configured = getServletContext().getInitParameter("UPLOAD_DIR");
		Path dir = (configured == null || configured.isBlank())
				? Paths.get(System.getProperty("user.home"), "wrt-upload")
				: Paths.get(configured);
		dir = dir.toAbsolutePath().normalize();
		Files.createDirectories(dir);
		return dir;
	}

}//class
