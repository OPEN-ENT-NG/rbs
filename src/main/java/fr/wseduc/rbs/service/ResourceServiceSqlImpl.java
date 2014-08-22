package fr.wseduc.rbs.service;

import static org.entcore.common.sql.Sql.parseId;
import static org.entcore.common.sql.SqlResult.validRowsResultHandler;

import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.sql.Sql;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.webutils.Either;

public class ResourceServiceSqlImpl extends SqlCrudService implements ResourceService {

	public ResourceServiceSqlImpl() {
		super("rbs", "resource");
	}

	@Override
	public void updateResource(final String id, final JsonObject data,
			final Handler<Either<String, JsonObject>> handler) {

		StringBuilder sb = new StringBuilder();
		JsonArray values = new JsonArray();
		for (String attr : data.getFieldNames()) {
			if ("was_available".equals(attr)) {
				continue;
			}
			sb.append(attr).append(" = ?, ");
			values.add(data.getValue(attr));
		}
		String query =
				"UPDATE rbs.resource" +
				" SET " + sb.toString() + "modified = NOW() " +
				"WHERE id = ? ";
		Sql.getInstance().prepared(query, values.add(parseId(id)), validRowsResultHandler(handler));
	}

}
