package net.atos.entng.rbs.controllers;

import java.util.Map;

import net.atos.entng.rbs.Rbs;

import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.platform.Container;

import fr.wseduc.rs.Get;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.BaseController;

public class DisplayController extends BaseController {

	private EventStore eventStore;
	private enum RbsEvent { ACCESS }

	@Override
	public void init(Vertx vertx, Container container, RouteMatcher rm,
					 Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
		super.init(vertx, container, rm, securedActions);
		eventStore = EventStoreFactory.getFactory().getEventStore(Rbs.class.getSimpleName());
	}

	@Get("")
	@SecuredAction("rbs.view")
	public void view(final HttpServerRequest request) {
		renderView(request);

		// Create event "access to application Rbs" and store it, for module "statistics"
		eventStore.createAndStoreEvent(RbsEvent.ACCESS.name(), request);
	}
}
