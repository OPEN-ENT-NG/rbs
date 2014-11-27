package net.atos.entng.rbs.service;

import java.util.List;

import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.webutils.Either;

public interface ResourceTypeService {

	public void list(List<String> groupsAndUserIds, UserInfos user,
			Handler<Either<String, JsonArray>> handler);

	public void getModeratorsIds(String typeId, Handler<Either<String, JsonArray>> handler);
}
