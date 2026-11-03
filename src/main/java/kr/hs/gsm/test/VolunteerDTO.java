package kr.hs.gsm.test;

//volunteers 테이블 (+ 목록 조회용 학생 정보, 파일 개수)
public class VolunteerDTO {
	private long id;
	private long studentId;
	private String org;				//봉사 기관
	private String volDate;			//봉사일 (YYYY-MM-DD)
	private int minutes;			//봉사 시간(분)
	private String content;
	private String status;			//DRAFT / SUBMITTED / APPROVED / REJECTED
	private String rejectReason;
	private Long reviewerId;		//승인·반려 교사 (없으면 null)
	private String reviewedAt;
	private String createdAt;

	//조회용 (join)
	private String studentName;
	private Integer grade;
	private Integer classNo;
	private Integer studentNo;
	private int fileCount;

	public long getId() {
		return id;
	}
	public void setId(long id) {
		this.id = id;
	}
	public long getStudentId() {
		return studentId;
	}
	public void setStudentId(long studentId) {
		this.studentId = studentId;
	}
	public String getOrg() {
		return org;
	}
	public void setOrg(String org) {
		this.org = org;
	}
	public String getVolDate() {
		return volDate;
	}
	public void setVolDate(String volDate) {
		this.volDate = volDate;
	}
	public int getMinutes() {
		return minutes;
	}
	public void setMinutes(int minutes) {
		this.minutes = minutes;
	}
	public String getContent() {
		return content;
	}
	public void setContent(String content) {
		this.content = content;
	}
	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}
	public String getRejectReason() {
		return rejectReason;
	}
	public void setRejectReason(String rejectReason) {
		this.rejectReason = rejectReason;
	}
	public Long getReviewerId() {
		return reviewerId;
	}
	public void setReviewerId(Long reviewerId) {
		this.reviewerId = reviewerId;
	}
	public String getReviewedAt() {
		return reviewedAt;
	}
	public void setReviewedAt(String reviewedAt) {
		this.reviewedAt = reviewedAt;
	}
	public String getCreatedAt() {
		return createdAt;
	}
	public void setCreatedAt(String createdAt) {
		this.createdAt = createdAt;
	}
	public String getStudentName() {
		return studentName;
	}
	public void setStudentName(String studentName) {
		this.studentName = studentName;
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
	public Integer getStudentNo() {
		return studentNo;
	}
	public void setStudentNo(Integer studentNo) {
		this.studentNo = studentNo;
	}
	public int getFileCount() {
		return fileCount;
	}
	public void setFileCount(int fileCount) {
		this.fileCount = fileCount;
	}
}//class
