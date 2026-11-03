package kr.hs.gsm.test.api;

//API 오류 응답용 예외 - ApiServlet 이 잡아서 {code, message} JSON 으로 응답한다
public class ApiException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	private final int status;	//HTTP 상태 코드
	private final String code;	//오류 코드 (예: REASON_REQUIRED)

	public ApiException(int status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public int getStatus() {
		return status;
	}
	public String getCode() {
		return code;
	}

	public static ApiException badRequest(String code, String message) {
		return new ApiException(400, code, message);
	}
	public static ApiException unauthorized() {
		return new ApiException(401, "UNAUTHORIZED", "로그인이 필요합니다.");
	}
	public static ApiException forbidden(String message) {
		return new ApiException(403, "FORBIDDEN", message);
	}
	public static ApiException notFound(String message) {
		return new ApiException(404, "NOT_FOUND", message);
	}
	public static ApiException conflict(String code, String message) {
		return new ApiException(409, code, message);
	}
}//class
