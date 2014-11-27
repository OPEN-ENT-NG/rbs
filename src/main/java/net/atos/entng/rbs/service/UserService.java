package net.atos.entng.rbs.service;

import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

public interface UserService {

	public void getUsers(JsonArray userIds, JsonArray groupIds,
			Handler<JsonObject> handler);

}
