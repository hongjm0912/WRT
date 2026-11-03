package kr.hs.gsm.test;

//봉사 목록 검색 조건 (GET /volunteers, bulk-approve filter, 엑셀 출력 공용) - 값이 null 이면 조건 제외
public class VolunteerSearchDTO {
	private Long studentId;		//학생 본인 조회 시 강제 지정
	private Integer grade;
	private Integer classNo;
	private String status;
	private String from;		//봉사일 시작 (YYYY-MM-DD)
	private String to;			//봉사일 끝 (YYYY-MM-DD)
	private String org;			//기관명 (부분 일치)
	private Integer minMinutes;
	private Integer maxMinutes;
	private Boolean hasFile;

	public Long getStudentId() {
		return studentId;
	}
	public void setStudentId(Long studentId) {
		this.studentId = studentId;
	}
	public Integer getGrade() {
		return grade;
	}
	public void setGrade(Integer grade) {
		this.grade = grade;
	}
	public Integer getClassNo() {
		return classNo;
	}
	public void setClassNo(Integer classNo) {
		this.classNo = classNo;
	}
	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}
	public String getFrom() {
		return from;
	}
	public void setFrom(String from) {
		this.from = from;
	}
	public String getTo() {
		return to;
	}
	public void setTo(String to) {
		this.to = to;
	}
	public String getOrg() {
		return org;
	}
	public void setOrg(String org) {
		this.org = org;
	}
	public Integer getMinMinutes() {
		return minMinutes;
	}
	public void setMinMinutes(Integer minMinutes) {
		this.minMinutes = minMinutes;
	}
	public Integer getMaxMinutes() {
		return maxMinutes;
	}
	public void setMaxMinutes(Integer maxMinutes) {
		this.maxMinutes = maxMinutes;
	}
	public Boolean getHasFile() {
		return hasFile;
	}
	public void setHasFile(Boolean hasFile) {
		this.hasFile = hasFile;
	}
}//class
