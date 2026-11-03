package kr.hs.gsm.test.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import kr.hs.gsm.test.UserDAO;
import kr.hs.gsm.test.UserDTO;

/**
 * POST /api/v1/users/import - 명단 업로드 (교사)
 *   multipart/form-data, 필드명 file, UTF-8 CSV (엑셀에서 "CSV UTF-8" 로 저장)
 *   첫 줄 헤더: email,name,role,grade,class,number   (role: STUDENT / TEACHER, 교사는 학년·반·번호 비움)
 *   한 줄이라도 형식이 틀리거나 이메일이 중복되면 전체를 등록하지 않는다.
 */
@WebServlet("/api/v1/users/import")
@MultipartConfig(maxFileSize = 5 * 1024 * 1024, maxRequestSize = 6 * 1024 * 1024)
public class UserImportServlet extends ApiServlet {
	private static final long serialVersionUID = 1L;

	private static final String[] HEADER = {"email", "name", "role", "grade", "class", "number"};

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		requireTeacher(req);
		String contentType = req.getContentType();
		if(contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data")) {
			throw ApiException.badRequest("MULTIPART_REQUIRED", "multipart/form-data 로 업로드하세요.");
		}
		Part part;
		try {
			part = req.getPart("file");
		} catch (IllegalStateException e) {
			throw new ApiException(413, "FILE_TOO_LARGE", "파일은 5MB 이하만 올릴 수 있습니다.");
		}
		if(part == null || part.getSize() == 0) throw ApiException.badRequest("FILE_REQUIRED", "file 필드에 CSV 파일을 넣어 주세요.");

		List<UserDTO> users = new ArrayList<UserDTO>();
		List<String> errors = new ArrayList<String>();
		Set<String> emails = new HashSet<String>();

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(part.getInputStream(), StandardCharsets.UTF_8))) {
			String header = reader.readLine();
			if(header == null) throw ApiException.badRequest("EMPTY_FILE", "빈 파일입니다.");
			header = header.replace("﻿", "").trim().toLowerCase(Locale.ROOT);	//엑셀 BOM 제거
			if(!header.replace(" ", "").equals(String.join(",", HEADER))) {
				throw ApiException.badRequest("INVALID_HEADER", "첫 줄은 " + String.join(",", HEADER) + " 이어야 합니다.");
			}

			String line;
			int lineNo = 1;
			while((line = reader.readLine()) != null) {
				lineNo++;
				if(line.isBlank()) continue;
				String[] cols = line.split(",", -1);
				if(cols.length != HEADER.length) {
					errors.add(lineNo + "행: 칸 수가 " + HEADER.length + "개가 아닙니다.");
					continue;
				}
				try {
					users.add(parseRow(cols, emails));
				} catch (IllegalArgumentException e) {
					errors.add(lineNo + "행: " + e.getMessage());
				}
			}
		}

		if(!errors.isEmpty()) {
			Map<String, Object> body = new LinkedHashMap<String, Object>();
			body.put("code", "INVALID_ROWS");
			body.put("message", "형식이 틀린 행이 있어 등록하지 않았습니다.");
			body.put("errors", errors);
			json(resp, 400, body);
			return;
		}
		if(users.isEmpty()) throw ApiException.badRequest("EMPTY_FILE", "등록할 사용자가 없습니다.");

		int imported = new UserDAO().importUsers(users);
		if(imported == 0) throw ApiException.conflict("IMPORT_FAILED", "이미 등록된 이메일이 있어 등록하지 않았습니다.");
		json(resp, 201, result("imported", imported));
	}//doPost()

	private UserDTO parseRow(String[] cols, Set<String> emails) {
		String email = cols[0].trim().toLowerCase(Locale.ROOT);
		String name = cols[1].trim();
		String role = cols[2].trim().toUpperCase(Locale.ROOT);

		if(!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다.");
		if(!emails.add(email)) throw new IllegalArgumentException("파일 안에 같은 이메일이 또 있습니다.");
		if(name.isEmpty()) throw new IllegalArgumentException("이름이 비어 있습니다.");
		if(!role.equals(ROLE_STUDENT) && !role.equals(ROLE_TEACHER)) throw new IllegalArgumentException("role 은 STUDENT 또는 TEACHER 입니다.");

		UserDTO u = new UserDTO();
		u.setEmail(email);
		u.setName(name);
		u.setRole(role);
		if(role.equals(ROLE_STUDENT)) {
			u.setGrade(number(cols[3], "grade", 1, 3));
			u.setClassNo(number(cols[4], "class", 1, 99));
			u.setStudentNo(number(cols[5], "number", 1, 99));
		}
		return u;
	}

	private Integer number(String value, String name, int min, int max) {
		try {
			int n = Integer.parseInt(value.trim());
			if(n < min || n > max) throw new IllegalArgumentException(name + " 는 " + min + "~" + max + " 사이여야 합니다.");
			return n;
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("학생은 " + name + " 를 숫자로 입력해야 합니다.");
		}
	}

}//class
