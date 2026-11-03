package kr.hs.gsm.test;

//학생 요약 (GET /students/{id}/summary, GET /me) - 테이블 아님, 집계 결과
public class StudentSummaryDTO {
	private long studentId;
	private String name;
	private int volunteerMinutes;	//승인된 봉사 누적(분)
	private int meritTotal;			//유효 상점 합계
	private int demeritTotal;		//유효 벌점 합계

	public long getStudentId() {
		return studentId;
	}
	public void setStudentId(long studentId) {
		this.studentId = studentId;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public int getVolunteerMinutes() {
		return volunteerMinutes;
	}
	public void setVolunteerMinutes(int volunteerMinutes) {
		this.volunteerMinutes = volunteerMinutes;
	}
	public int getMeritTotal() {
		return meritTotal;
	}
	public void setMeritTotal(int meritTotal) {
		this.meritTotal = meritTotal;
	}
	public int getDemeritTotal() {
		return demeritTotal;
	}
	public void setDemeritTotal(int demeritTotal) {
		this.demeritTotal = demeritTotal;
	}
}//class
