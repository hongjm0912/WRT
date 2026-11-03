package kr.hs.gsm.test.api;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import kr.hs.gsm.test.UserDAO;
import kr.hs.gsm.test.UserDTO;

/**
 * POST /api/v1/auth/google  - Google OAuth 로그인 (권한: 전체)
 *   요청: { "idToken": "<Google ID 토큰>" }
 *   - Google tokeninfo 로 토큰을 검증하고, 토큰의 이메일이 users 에 등록된 사용자면 세션 로그인
 *   - web.xml context-param
 *       GOOGLE_CLIENT_ID : 발급받은 OAuth 클라이언트 ID (필수, 토큰의 aud 와 비교)
 *       ALLOWED_DOMAIN   : 허용 이메일 도메인 (선택, 예: gsm.hs.kr)
 *       AUTH_DEV_MODE    : true 면 { "email": "..." } 만으로 로그인 (로컬 테스트 전용, 배포 시 false)
 */
@WebServlet("/api/v1/auth/*")
public class AuthServlet extends ApiServlet {
	private static final long serialVersionUID = 1L;

	private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=";
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String[] parts = pathParts(req);
		if(parts.length != 1 || !parts[0].equals("google")) throw ApiException.notFound("잘못된 경로입니다.");

		JsonObject body = readJson(req);
		String email = "true".equalsIgnoreCase(getServletContext().getInitParameter("AUTH_DEV_MODE")) && str(body, "idToken") == null
				? required(str(body, "email"), "email")
				: verifyGoogleToken(required(str(body, "idToken"), "idToken"));

		UserDTO user = new UserDAO().getUserByEmail(email);
		if(user == null) throw ApiException.forbidden("등록되지 않은 사용자입니다. 명단에 있는 계정으로 로그인하세요.");

		//세션 고정 공격 방지: 기존 세션을 버리고 새로 발급
		HttpSession old = req.getSession(false);
		if(old != null) old.invalidate();
		req.getSession(true).setAttribute(SESSION_USER, user);

		Map<String, Object> userBody = new LinkedHashMap<String, Object>();
		userBody.put("id", user.getId());
		userBody.put("name", user.getName());
		userBody.put("role", user.getRole());
		json(resp, 200, result("user", userBody));
	}//doPost()

	//Google ID 토큰 검증 후 이메일 반환
	private String verifyGoogleToken(String idToken) throws IOException {
		String clientId = getServletContext().getInitParameter("GOOGLE_CLIENT_ID");
		if(clientId == null || clientId.isBlank()) {
			throw new ApiException(500, "OAUTH_NOT_CONFIGURED", "서버에 GOOGLE_CLIENT_ID 가 설정되지 않았습니다.");
		}

		JsonObject info;
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(TOKENINFO_URL + URLEncoder.encode(idToken, StandardCharsets.UTF_8)))
					.timeout(Duration.ofSeconds(5)).GET().build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if(response.statusCode() != 200) throw new ApiException(401, "INVALID_TOKEN", "유효하지 않은 Google 토큰입니다.");
			info = JsonParser.parseString(response.body()).getAsJsonObject();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ApiException(502, "OAUTH_UNAVAILABLE", "Google 인증 서버에 연결할 수 없습니다.");
		} catch (IOException e) {
			throw new ApiException(502, "OAUTH_UNAVAILABLE", "Google 인증 서버에 연결할 수 없습니다.");
		}

		if(!clientId.equals(str(info, "aud"))) throw new ApiException(401, "INVALID_TOKEN", "이 서비스용으로 발급된 토큰이 아닙니다.");
		if(!"true".equalsIgnoreCase(str(info, "email_verified"))) throw new ApiException(401, "INVALID_TOKEN", "이메일 인증이 안 된 계정입니다.");

		String email = str(info, "email");
		if(email == null) throw new ApiException(401, "INVALID_TOKEN", "토큰에 이메일 정보가 없습니다.");

		String domain = getServletContext().getInitParameter("ALLOWED_DOMAIN");
		if(domain != null && !domain.isBlank() && !email.toLowerCase().endsWith("@" + domain.toLowerCase())) {
			throw ApiException.forbidden(domain + " 계정으로만 로그인할 수 있습니다.");
		}
		return email;
	}//verifyGoogleToken()

}//class
