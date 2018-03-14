/*
 * Copyright © Région Nord Pas de Calais-Picardie,  Département 91, Région Aquitaine-Limousin-Poitou-Charentes, 2016.
 *
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package net.atos.entng.rbs.controllers;

import java.util.Map;

import io.vertx.core.json.JsonObject;
import net.atos.entng.rbs.Rbs;

import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;


import fr.wseduc.rs.Get;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.BaseController;

public class DisplayController extends BaseController {

	private EventStore eventStore;
	private enum RbsEvent { ACCESS }

	@Override
	public void init(Vertx vertx, JsonObject config, RouteMatcher rm,
					 Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
		super.init(vertx, config, rm, securedActions);
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
