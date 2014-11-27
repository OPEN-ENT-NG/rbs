package net.atos.entng.rbs.service;

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

public class UserServiceDirectoryImpl implements UserService {

	private final EventBus eb;
	private static final String DIRECTORY_ADDRESS = "directory";

	public UserServiceDirectoryImpl(EventBus eb) {
		this.eb = eb;
	}

	@Override
	public void getUsers(final JsonArray userIds, final JsonArray groupIds,
			final Handler<JsonObject> handler) {

		JsonObject action = new JsonObject()
				.putString("action", "list-users")
				.putArray("userIds", userIds)
				.putArray("groupIds", groupIds);

		eb.send(DIRECTORY_ADDRESS, action, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> res) {
				handler.handle(res.body());
			}
		});
	}

}
