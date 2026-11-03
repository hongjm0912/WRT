package kr.hs.gsm.test;

//volunteer_files 테이블
public class VolunteerFileDTO {
	private long id;
	private long volunteerId;
	private String fileName;	//원본 파일명
	private String filePath;	//저장 경로
	private String uploadedAt;

	public long getId() {
		return id;
	}
	public void setId(long id) {
		this.id = id;
	}
	public long getVolunteerId() {
		return volunteerId;
	}
	public void setVolunteerId(long volunteerId) {
		this.volunteerId = volunteerId;
	}
	public String getFileName() {
		return fileName;
	}
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
	public String getFilePath() {
		return filePath;
	}
	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}
	public String getUploadedAt() {
		return uploadedAt;
	}
	public void setUploadedAt(String uploadedAt) {
		this.uploadedAt = uploadedAt;
	}
}//class
