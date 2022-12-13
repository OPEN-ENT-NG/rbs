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

package net.atos.entng.rbs.service;

import fr.wseduc.webutils.Either;
import net.atos.entng.rbs.models.Availability;
import org.entcore.common.service.CrudService;
import org.entcore.common.user.UserInfos;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public interface AvailabilityService extends CrudService {
	/**
	 * List all the (un)availabilities of a user
	 *
	 * @param resourceIds : resources' id
	 * @param handler  : handler which contains the response
	 */
	void listAvailabilities(JsonArray resourceIds, Handler<Either<String, JsonArray>> handler);

	/**
	 * List all the (un)availabilities of a resource
	 *
	 * @param resourceId : resource id
	 * @param is_unavailability : type of (un)availability to list
	 * @param user     : information of current user logged
	 * @param handler  : handler which contains the response
	 */
	void listResourceAvailability(Integer resourceId, boolean is_unavailability, UserInfos user, Handler<Either<String, JsonArray>> handler);

	/**
	 * Create an (un)availability
	 *
	 * @param availability : current (un)availability
	 * @param user     : information of current user logged
	 * @param handler  : handler which contains the response
	 */
	void createAvailability(Availability availability, UserInfos user, Handler<Either<String, JsonObject>> handler);

	/**
	 * Update an (un)availability
	 *
	 * @param availability : current (un)availability
	 * @param user     : information of current user logged
	 * @param handler  : handler which contains the response
	 */
	void updateAvailability(Availability availability, UserInfos user, Handler<Either<String, JsonObject>> handler);


	/**
	 * Delete an (un)availability
	 *
	 * @param availabilityId : (un)availability id
	 * @param handler  : handler which contains the response
	 */
	void deleteAvailability(Integer availabilityId, Handler<Either<String, JsonObject>> handler);


	/**
	 * Delete all (un)availabilities of a resource
	 *
	 * @param resourceId : resource id
	 * @param deleteUnavailability : should we delete availabilities, unavailabilities or both
	 * @param handler  : handler which contains the response
	 */
	void deleteAllAvailability(Integer resourceId, String deleteUnavailability, Handler<Either<String, JsonArray>> handler);
}
