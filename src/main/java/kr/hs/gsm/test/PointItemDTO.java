package kr.hs.gsm.test;

//point_items 테이블
public class PointItemDTO {
	private long id;
	private String name;
	private String type;		//MERIT(상점) / DEMERIT(벌점)
	private int score;
	private String isActive;	//Y / N

	public long getId() {
		return id;
	}
	public void setId(long id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public int getScore() {
		return score;
	}
	public void setScore(int score) {
		this.score = score;
	}
	public String getIsActive() {
		return isActive;
	}
	public void setIsActive(String isActive) {
		this.isActive = isActive;
	}
}//class
