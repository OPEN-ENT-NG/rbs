package net.atos.entng.rbs.service;

import java.util.List;

import org.entcore.common.service.CrudService;
import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.webutils.Either;

public interface ResourceService extends CrudService {

	public void createResource(JsonObject resource, UserInfos user, Handler<Either<String, JsonObject>> handler);

	public void listResources(List<String> groupsAndUserIds, UserInfos user, Handler<Either<String, JsonArray>> handler);

	public void updateResource(String id, JsonObject data, Handler<Either<String, JsonObject>> handler);

	public void getBookingOwnersIds(long resourceId, Handler<Either<String, JsonArray>> handler);

	/**
	 * Get max_delay and min_delay of resource, owner, school_id and managers (userIds and groupIds) of resourceType
	 */
	public void getDelaysAndTypeProperties(long resourceId, Handler<Either<String, JsonObject>> handler);
}
