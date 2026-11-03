package kr.hs.gsm.test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

//point_items, points 테이블
public class PointDAO extends ProjectDAO {

	private static final String SELECT_POINT =
		  "select p.id, p.student_id, p.item_id, p.teacher_id, p.semester, p.score, p.memo, p.status, "
		+ "p.cancel_reason, to_char(p.canceled_at, 'YYYY-MM-DD HH24:MI:SS') as canceled_at, "
		+ "to_char(p.created_at, 'YYYY-MM-DD HH24:MI:SS') as created_at, "
		+ "s.name as student_name, i.name as item_name, i.type as item_type, t.name as teacher_name "
		+ "from points p "
		+ "join users s on s.id = p.student_id "
		+ "join point_items i on i.id = p.item_id "
		+ "join users t on t.id = p.teacher_id ";

	private PointItemDTO toItem(ResultSet rs) throws SQLException {
		PointItemDTO dto = new PointItemDTO();
		dto.setId(rs.getLong("id"));
		dto.setName(rs.getString("name"));
		dto.setType(rs.getString("type"));
		dto.setScore(rs.getInt("score"));
		dto.setIsActive(rs.getString("is_active"));
		return dto;
	}//toItem()

	private PointDTO toPoint(ResultSet rs) throws SQLException {
		PointDTO dto = new PointDTO();
		dto.setId(rs.getLong("id"));
		dto.setStudentId(rs.getLong("student_id"));
		dto.setItemId(rs.getLong("item_id"));
		dto.setTeacherId(rs.getLong("teacher_id"));
		dto.setSemester(rs.getString("semester"));
		dto.setScore(rs.getInt("score"));
		dto.setMemo(rs.getString("memo"));
		dto.setStatus(rs.getString("status"));
		dto.setCancelReason(rs.getString("cancel_reason"));
		dto.setCanceledAt(rs.getString("canceled_at"));
		dto.setCreatedAt(rs.getString("created_at"));
		dto.setStudentName(rs.getString("student_name"));
		dto.setItemName(rs.getString("item_name"));
		dto.setItemType(rs.getString("item_type"));
		dto.setTeacherName(rs.getString("teacher_name"));
		return dto;
	}//toPoint()

	// ------------------------------------------------------------------
	// 상벌점 항목 (/point-items)
	// ------------------------------------------------------------------

	//항목 목록 (GET /point-items) - activeOnly 가 true 면 사용 중인 항목만
	public ArrayList<PointItemDTO> getPointItemList(boolean activeOnly) {
		ArrayList<PointItemDTO> list = new ArrayList<PointItemDTO>();
		String sql = "select id, name, type, score, is_active from point_items "
				   + (activeOnly ? "where is_active = 'Y' " : "")
				   + "order by type desc, id";
		try {
			conn = getConn();
			ps = conn.prepareStatement(sql);
			rs = ps.executeQuery();
			while(rs.next()) list.add(toItem(rs));
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("getPointItemList() Exception!!!");
		} finally {
			dbClose();
		}
		return list;
	}//getPointItemList()

	//항목 등록 (POST /point-items) - 새 id 반환
	public long insertPointItem(PointItemDTO dto) {
		long id = 0;
		String sql = "insert into point_items (id, name, type, score, is_active) values (?, ?, ?, ?, ?)";
		try {
			conn = getConn();
			long newId = nextVal("seq_point_items");
			ps = conn.prepareStatement(sql);
			ps.setLong(1, newId);
			ps.setString(2, dto.getName());
			ps.setString(3, dto.getType());
			ps.setInt(4, dto.getScore());
			ps.setString(5, dto.getIsActive() == null ? "Y" : dto.getIsActive());
			if(ps.executeUpdate() == 1) id = newId;
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("insertPointItem() Exception!!!");
		} finally {
			dbClose();
		}
		return id;
	}//insertPointItem()

	//항목 수정 (PATCH /point-items) - 점수를 바꿔도 이미 부여된 기록의 score 는 그대로
	public int updatePointItem(PointItemDTO dto) {
		int result = 0;
		String sql = "update point_items set name = ?, type = ?, score = ?, is_active = ? where id = ?";
		try {
			conn = getConn();
			ps = conn.prepareStatement(sql);
			ps.setString(1, dto.getName());
			ps.setString(2, dto.getType());
			ps.setInt(3, dto.getScore());
			ps.setString(4, dto.getIsActive() == null ? "Y" : dto.getIsActive());
			ps.setLong(5, dto.getId());
			result = ps.executeUpdate();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("updatePointItem() Exception!!!");
		} finally {
			dbClose();
		}
		return result;
	}//updatePointItem()

	// ------------------------------------------------------------------
	// 상벌점 기록 (/points)
	// ------------------------------------------------------------------

	//기록 목록 (GET /points?studentId&semester, GET /exports/points.xlsx) - null 이면 조건 제외
	public ArrayList<PointDTO> getPointList(Long studentId, String semester) {
		ArrayList<PointDTO> list = new ArrayList<PointDTO>();
		List<Object> params = new ArrayList<Object>();
		StringBuilder sql = new StringBuilder(SELECT_POINT).append("where 1 = 1 ");
		if(studentId != null) {
			sql.append("and p.student_id = ? ");
			params.add(studentId);
		}
		if(semester != null && !semester.isBlank()) {
			sql.append("and p.semester = ? ");
			params.add(semester);
		}
		sql.append("order by p.created_at desc, p.id desc");
		try {
			conn = getConn();
			ps = conn.prepareStatement(sql.toString());
			bind(ps, params);
			rs = ps.executeQuery();
			while(rs.next()) list.add(toPoint(rs));
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("getPointList() Exception!!!");
		} finally {
			dbClose();
		}
		return list;
	}//getPointList()

	//기록 1건 조회 (취소 권한 확인용)
	public PointDTO getPoint(long id) {
		PointDTO dto = null;
		try {
			conn = getConn();
			ps = conn.prepareStatement(SELECT_POINT + "where p.id = ?");
			ps.setLong(1, id);
			rs = ps.executeQuery();
			if(rs.next()) dto = toPoint(rs);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("getPoint() Exception!!!");
		} finally {
			dbClose();
		}
		return dto;
	}//getPoint()

	//부여 - studentIds 배열 방식 (POST /points), 항목 점수를 복사해 저장, 부여 건수 반환
	public int insertPoints(List<Long> studentIds, long itemId, long teacherId, String semester, String memo) {
		int result = 0;
		if(studentIds == null || studentIds.isEmpty()) return 0;
		String sql = "insert into points (id, student_id, item_id, teacher_id, semester, score, memo) "
				   + "select seq_points.nextval, u.id, i.id, ?, ?, i.score, ? "
				   + "from users u, point_items i "
				   + "where u.id = ? and u.role = 'STUDENT' and i.id = ? and i.is_active = 'Y'";
		try {
			conn = getConn();
			conn.setAutoCommit(false);
			ps = conn.prepareStatement(sql);
			for(Long studentId : studentIds) {
				List<Object> params = new ArrayList<Object>();
				params.add(teacherId);
				params.add(semester);
				params.add(memo);
				params.add(studentId);
				params.add(itemId);
				bind(ps, params);
				result += ps.executeUpdate();
			}
			conn.commit();
		} catch (Exception e) {
			rollback();
			result = 0;
			e.printStackTrace();
			System.out.println("insertPoints(ids) Exception!!!");
		} finally {
			dbClose();
		}
		return result;
	}//insertPoints()

	//부여 - filter 객체 방식 (학년·반·검색어에 해당하는 학생 전체), 부여 건수 반환
	public int insertPoints(Integer grade, Integer classNo, String q,
							long itemId, long teacherId, String semester, String memo) {
		int result = 0;
		List<Object> params = new ArrayList<Object>();
		params.add(teacherId);
		params.add(semester);
		params.add(memo);
		String where = UserDAO.studentWhere(grade, classNo, q, params);
		params.add(itemId);
		String sql = "insert into points (id, student_id, item_id, teacher_id, semester, score, memo) "
				   + "select seq_points.nextval, u.id, i.id, ?, ?, i.score, ? "
				   + "from users u, point_items i "
				   + where
				   + "and i.id = ? and i.is_active = 'Y'";
		try {
			conn = getConn();
			ps = conn.prepareStatement(sql);
			bind(ps, params);
			result = ps.executeUpdate();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("insertPoints(filter) Exception!!!");
		} finally {
			dbClose();
		}
		return result;
	}//insertPoints()

	//취소 (POST /points/{id}/cancel) - 부여한 교사 본인 + ACTIVE 일 때만, reason 필수
	public int cancelPoint(long id, long teacherId, String reason) {
		int result = 0;
		if(reason == null || reason.isBlank()) return 0;
		String sql = "update points set status = 'CANCELED', cancel_reason = ?, canceled_at = systimestamp "
				   + "where id = ? and teacher_id = ? and status = 'ACTIVE'";
		try {
			conn = getConn();
			ps = conn.prepareStatement(sql);
			ps.setString(1, reason.trim());
			ps.setLong(2, id);
			ps.setLong(3, teacherId);
			result = ps.executeUpdate();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("cancelPoint() Exception!!!");
		} finally {
			dbClose();
		}
		return result;
	}//cancelPoint()

}//class
