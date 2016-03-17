package net.atos.entng.rbs.events;

import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.Either.Right;
import fr.wseduc.webutils.I18n;
import org.entcore.common.search.SearchingEvents;
import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.sql.Sql;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import static org.entcore.common.sql.SqlResult.validResult;

public class RbsSearchingEvents extends SqlCrudService implements SearchingEvents {

	private static final Logger log = LoggerFactory.getLogger(RbsSearchingEvents.class);

	private static final I18n i18n = I18n.getInstance();

	public RbsSearchingEvents() {
		super("rbs", "booking");
	}

	@Override
	public void searchResource(List<String> appFilters, String userId, JsonArray groupIds, JsonArray searchWords, Integer page, Integer limit, final JsonArray columnsHeader,
							   final String locale, final Handler<Either<String, JsonArray>> handler) {
		if (appFilters.contains(RbsSearchingEvents.class.getSimpleName())) {

			final List<String> idsUsers = new ArrayList<String>(groupIds.toList());
			idsUsers.add(userId);

			final StringBuilder query = new StringBuilder();

			final String iLikeTemplate = "ILIKE ALL " + Sql.arrayPrepared(searchWords.toArray());
			final List<String> searchFields = new ArrayList<String>();
			searchFields.add("r.name");
			searchFields.add("t.name");
			searchFields.add("b.booking_reason");
			final String searchWhere = "b.parent_booking_id IS NULL AND (" + searchWherePrepared(searchFields, iLikeTemplate) + ")";
			//find all booking without slots
			query.append("SELECT b.id, b.start_date, b.end_date, b.booking_reason, b.modified, b.owner, r.name as resource_name, t.name as resource_type, u.username AS owner_name")
					.append(" FROM rbs.booking AS b")
					.append(" INNER JOIN rbs.resource AS r ON r.id = b.resource_id")
					.append(" INNER JOIN rbs.resource_type AS t ON r.type_id = t.id")
					.append(" LEFT JOIN rbs.resource_type_shares AS rs ON rs.resource_id = r.type_id")
					.append(" LEFT JOIN rbs.users AS u ON u.id = b.owner")
					.append(" WHERE (").append(searchWhere).append(") AND (rs.member_id IN ").append(Sql.listPrepared(idsUsers.toArray()))
					.append(" OR t.owner = ?");
			// fixme it is important for search : A local administrator of a given school can see all resources of the school's types, even if he is not owner or manager of these types or resources
			// if necessary modify common to add userinfos.getFunctions
			/*List<String> scope = getLocalAdminScope(user);
			if (scope!=null && !scope.isEmpty()) {
				query.append(" OR t.school_id IN ").append(Sql.listPrepared(scope.toArray()));
				for (String schoolId : scope) {
					values.addString(schoolId);
				}
			}
			*/
			query.append(")  GROUP BY b.id, u.username, r.name, t.name ORDER BY modified DESC LIMIT ? OFFSET ?");
			final JsonArray values = new JsonArray();
			final List<String> valuesWildcard = searchValuesWildcard(searchWords.toList());
			for (int i=0;i<searchFields.size();i++) {
				for (final String value : valuesWildcard) {
					values.addString(value);
				}
			}

			for (String groupOruser : idsUsers) {
				values.addString(groupOruser);
			}

			values.addString(userId);

			final int offset = page * limit;
			values.add(limit).add(offset);

			Sql.getInstance().prepared(query.toString(), values, new Handler<Message<JsonObject>>() {
				@Override
				public void handle(Message<JsonObject> event) {
					final Either<String, JsonArray> ei = validResult(event);
					if (ei.isRight()) {
						final JsonArray res = formatSearchResult(ei.right().getValue(), columnsHeader, locale);
						handler.handle(new Right<String, JsonArray>(res));
					} else {
						handler.handle(new Either.Left<String, JsonArray>(ei.left().getValue()));
					}
					if (log.isDebugEnabled()) {
						log.debug("[RbsSearchingEvents][searchResource] The resources searched by user are finded");
					}
				}
			});
		} else {
			handler.handle(new Right<String, JsonArray>(new JsonArray()));
		}
	}

	private JsonArray formatSearchResult(final JsonArray results, final JsonArray columnsHeader, String locale) {
		final List<String> aHeader = columnsHeader.toList();
		final JsonArray traity = new JsonArray();

		final String dateFormat = "EEEEE dd MMMMM yyyy " + i18n.translate("rbs.search.date.to", locale) + " HH:mm";

		for (int i=0;i<results.size();i++) {
			final JsonObject j = results.get(i);
			final JsonObject jr = new JsonObject();
			if (j != null) {
				jr.putString(aHeader.get(0), formatTitle(j, dateFormat, locale));
				jr.putString(aHeader.get(1), j.getString("booking_reason"));
				jr.putObject(aHeader.get(2), new JsonObject().putValue("$date",
						DatatypeConverter.parseDateTime(j.getString("modified")).getTime().getTime()));
				jr.putString(aHeader.get(3), j.getString("owner_name"));
				jr.putString(aHeader.get(4), j.getString("owner"));
				jr.putString(aHeader.get(5), "/rbs#/booking/"+ j.getNumber("id",0));
				traity.add(jr);
			}
		}
		return traity;
	}

	private String formatTitle(final JsonObject j, final String dateFormat, final String locale) {
		return i18n.translate("rbs.search.title", locale,
				new SimpleDateFormat(dateFormat).format(DatatypeConverter.parseDateTime(j.getString("start_date")).getTime()),
				new SimpleDateFormat(dateFormat).format(DatatypeConverter.parseDateTime(j.getString("end_date")).getTime()),
				j.getString("resource_name"),
				j.getString("resource_type"));
	}

	private String searchWherePrepared(List<String> list, final String templateLike) {
		StringBuilder sb = new StringBuilder();
		if (list != null && list.size() > 0) {
			for (String s : list) {
				sb.append(s).append(" ").append(templateLike).append(" OR ");
			}
			sb.delete(sb.length() - 3, sb.length());
		}
		return sb.toString();
	}

	private List<String> searchValuesWildcard(List<String> list) {
		final List<String> result = new ArrayList<String>();
		for (String s : list) {
			result.add("%" + s + "%");
		}
		return result;
	}
}
