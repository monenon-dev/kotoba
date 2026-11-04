package com.kotoba.backend.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.kotoba.backend.support.IntegrationTestSupport;

import jakarta.persistence.Column;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * 엔티티 어노테이션과 Flyway 가 만든 PostgreSQL 스키마를 비교한다 (CI 의 postgres 작업에서만 실행).
 *
 * Hibernate 의 ddl-auto: validate 는 테이블, 컬럼, 타입만 검사하므로 그 밖의 것을 여기서 본다.
 * 검사 범위: 인덱스(이름 + 컬럼), UNIQUE(컬럼 집합), FK(컬럼 → 참조 테이블, 정확히 일치), NOT NULL, varchar 길이.
 * 기대값은 엔티티에서 읽는다. 엔티티에 인덱스를 추가하고 마이그레이션을 빠뜨리면 여기서 실패한다.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB", matches = "postgres")
class SchemaConsistencyTest extends IntegrationTestSupport {

	private static final Pattern INDEX_COLUMNS = Pattern.compile("\\(([^()]*)\\)\\s*$");

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void entityAnnotationsMatchFlywaySchema() {
		List<String> problems = new ArrayList<>();
		Map<Class<?>, String> tables = new HashMap<>();
		entityManagerFactory.getMetamodel()
			.getEntities()
			.forEach(e -> tables.put(e.getJavaType(), e.getJavaType().getAnnotation(Table.class).name()));
		assertThat(tables).hasSize(6);

		tables.forEach((entity, table) -> {
			checkIndexes(entity, table, problems);
			checkUniqueConstraints(entity, table, problems);
			checkForeignKeys(entity, table, tables, problems);
			checkColumns(entity, table, problems);
		});

		assertThat(problems).as("엔티티와 Flyway 스키마가 다릅니다:%n  %s", String.join("\n  ", problems)).isEmpty();
	}

	// ===== 인덱스: 엔티티의 @Index 이름이 같은 컬럼 순서로 있어야 한다 =====

	private void checkIndexes(Class<?> entity, String table, List<String> problems) {
		Map<String, List<String>> actual = new HashMap<>();
		jdbc.query("select indexname, indexdef from pg_indexes where schemaname = current_schema() and tablename = ?",
				rs -> {
					actual.put(rs.getString(1), parseIndexColumns(rs.getString(2)));
				}, table);

		for (Index index : entity.getAnnotation(Table.class).indexes()) {
			List<String> expected = splitColumns(index.columnList());
			List<String> found = actual.get(index.name());
			if (found == null) {
				problems.add("%s: 인덱스 %s %s 가 없음".formatted(table, index.name(), expected));
			}
			else if (!found.equals(expected)) {
				problems.add("%s: 인덱스 %s 의 컬럼이 다름 (엔티티 %s, DB %s)".formatted(table, index.name(), expected, found));
			}
		}
	}

	// ===== UNIQUE: 엔티티의 @UniqueConstraint 와 @OneToOne 조인 컬럼의 컬럼 집합에 UNIQUE 가 있어야 한다 =====

	private void checkUniqueConstraints(Class<?> entity, String table, List<String> problems) {
		Set<Set<String>> actual = new HashSet<>();
		jdbc.query("""
				select indexdef from pg_indexes
				where schemaname = current_schema() and tablename = ? and indexdef like 'CREATE UNIQUE INDEX%'
				""", rs -> {
			actual.add(new TreeSet<>(parseIndexColumns(rs.getString(1))));
		}, table);

		Set<Set<String>> expected = new HashSet<>();
		for (UniqueConstraint unique : entity.getAnnotation(Table.class).uniqueConstraints()) {
			expected.add(new TreeSet<>(Arrays.asList(unique.columnNames())));
		}
		for (Field field : persistentFields(entity)) {
			if (field.isAnnotationPresent(OneToOne.class)) {
				expected.add(new TreeSet<>(Set.of(field.getAnnotation(JoinColumn.class).name())));
			}
		}
		for (Set<String> columns : expected) {
			if (!actual.contains(columns)) {
				problems.add("%s: UNIQUE %s 가 없음".formatted(table, columns));
			}
		}
	}

	// ===== FK: 엔티티의 연관관계와 정확히 일치해야 한다 (의도적으로 FK 가 없는 컬럼에 FK 가 생겨도 실패) =====

	private void checkForeignKeys(Class<?> entity, String table, Map<Class<?>, String> tables,
			List<String> problems) {
		Set<String> actual = new TreeSet<>();
		jdbc.query("""
				select kcu.column_name, ccu.table_name
				from information_schema.table_constraints tc
				join information_schema.key_column_usage kcu
				  on tc.constraint_name = kcu.constraint_name and tc.table_schema = kcu.table_schema
				join information_schema.constraint_column_usage ccu
				  on tc.constraint_name = ccu.constraint_name and tc.table_schema = ccu.table_schema
				where tc.constraint_type = 'FOREIGN KEY' and tc.table_schema = current_schema() and tc.table_name = ?
				""", rs -> {
			actual.add(rs.getString(1) + " -> " + rs.getString(2));
		}, table);

		Set<String> expected = new TreeSet<>();
		for (Field field : persistentFields(entity)) {
			if (field.isAnnotationPresent(ManyToOne.class) || field.isAnnotationPresent(OneToOne.class)) {
				expected.add(field.getAnnotation(JoinColumn.class).name() + " -> " + tables.get(field.getType()));
			}
		}
		if (!actual.equals(expected)) {
			problems.add("%s: FK 가 다름 (엔티티 %s, DB %s)".formatted(table, expected, actual));
		}
	}

	// ===== NOT NULL, varchar 길이 =====

	private void checkColumns(Class<?> entity, String table, List<String> problems) {
		Map<String, ColumnInfo> actual = new HashMap<>();
		jdbc.query("""
				select column_name, is_nullable, data_type, character_maximum_length
				from information_schema.columns
				where table_schema = current_schema() and table_name = ?
				""", rs -> {
			Object length = rs.getObject(4);
			actual.put(rs.getString(1), new ColumnInfo("YES".equals(rs.getString(2)), rs.getString(3),
					length == null ? null : ((Number) length).intValue()));
		}, table);

		for (Field field : persistentFields(entity)) {
			String column = columnName(field);
			ColumnInfo info = actual.get(column);
			if (info == null) {
				problems.add("%s.%s: 컬럼이 없음".formatted(table, column));
				continue;
			}
			boolean expectedNullable = isNullable(field);
			if (info.nullable() != expectedNullable) {
				problems.add("%s.%s: NOT NULL 이 다름 (엔티티 %s, DB %s)".formatted(table, column,
						nullability(expectedNullable), nullability(info.nullable())));
			}
			if (info.dataType().equals("character varying")) {
				Column annotation = field.getAnnotation(Column.class);
				int expectedLength = annotation == null ? 255 : annotation.length();
				if (info.maxLength() == null || info.maxLength() != expectedLength) {
					problems.add("%s.%s: 길이가 다름 (엔티티 %d, DB %s)".formatted(table, column, expectedLength,
							info.maxLength()));
				}
			}
		}
	}

	// ===== 엔티티 해석 (이 프로젝트의 엔티티 작성 방식만 지원: 상속·임베디드 없음) =====

	private static List<Field> persistentFields(Class<?> entity) {
		return Arrays.stream(entity.getDeclaredFields())
			.filter(f -> !Modifier.isStatic(f.getModifiers()) && !Modifier.isTransient(f.getModifiers()))
			.toList();
	}

	private static String columnName(Field field) {
		JoinColumn join = field.getAnnotation(JoinColumn.class);
		if (join != null) {
			return join.name();
		}
		Column column = field.getAnnotation(Column.class);
		if (column != null && !column.name().isEmpty()) {
			return column.name();
		}
		// Spring Boot 기본 네이밍 전략: camelCase → snake_case
		return field.getName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
	}

	private static boolean isNullable(Field field) {
		if (field.isAnnotationPresent(Id.class) || field.isAnnotationPresent(Version.class)
				|| field.getType().isPrimitive()) {
			return false;
		}
		JoinColumn join = field.getAnnotation(JoinColumn.class);
		if (join != null) {
			return join.nullable();
		}
		Column column = field.getAnnotation(Column.class);
		return column == null || column.nullable();
	}

	private static List<String> parseIndexColumns(String indexDef) {
		Matcher matcher = INDEX_COLUMNS.matcher(indexDef);
		return matcher.find() ? splitColumns(matcher.group(1)) : List.of();
	}

	private static List<String> splitColumns(String columns) {
		return Arrays.stream(columns.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
	}

	private static String nullability(boolean nullable) {
		return nullable ? "NULL 허용" : "NOT NULL";
	}

	private record ColumnInfo(boolean nullable, String dataType, Integer maxLength) {
	}

}
