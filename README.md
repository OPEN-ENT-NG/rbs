# À propos de l'application Rbs

* Licence : [AGPL v3](http://www.gnu.org/licenses/agpl.txt) - Copyright Conseil Régional Nord Pas de Calais - Picardie, Conseil départemental de l'Essonne, Conseil régional d'Aquitaine-Limousin-Poitou-Charentes
* Développeur(s) : ATOS
* Financeur(s) : Région Nord Pas de Calais-Picardie,  Département 91, Région Aquitaine-Limousin-Poitou-Charentes
* Description : Application de réservation de ressources : salles et matériels.(Remote Booking System)

# Documentation technique
## Construction

<pre>
		gradle copyMod
</pre>

## Déploier dans ent-core


## Configuration

Dans le fichier `/ent-core.json.template` :


Déclarer l'application dans la liste :
<pre>
	{
      "name": "net.atos~rbs~0.1-SNAPSHOT",
      "config": {
        "main" : "net.atos.entng.rbs.Rbs",
        "port" : 8026,
        "sql" : true,
        "mongodb" : false,
        "neo4j" : true,
        "app-name" : "Réservation de ressources",
        "app-address" : "/rbs",
        "app-icon" : "rbs-large",
        "host": "http://localhost:8090",
        "ssl" : false,
        "auto-redeploy": false,
        "userbook-host": "http://localhost:8090",
        "integration-mode" : "HTTP",
        "mode" : "dev"
      }
    }
</pre>


Associer une route d'entée à la configuration du module proxy intégré (`"name": "fr.wseduc~rbs~0.1-SNAPSHOT"`) :
<pre>
	{
		"location": "/rbs",
		"proxy_pass": "http://localhost:8026"
	}
</pre>


# Présentation du module

## Fonctionnalités

RBS est une application de gestion de réservations de ressources.
Elle permet de gérer des Ressources organisées en Types de ressources, d'effectuer des Réservations sur ces Ressources, et de consulter les Réservations.
Les permissions sont configurées sur les Types de ressources (via des partages Ent-core). Les Réservations peuvent être soumises à validations, et peuvent être ponctuelles ou périodiques.
La consultation peut se faire grâce à un Calendrier, ou grâce à une Liste des réservations.

RBS met en œuvre un comportement de recherche sur le nom des ressources, des types de ressources et sur les motifs de réservation.

## Modèle de persistance

Les données du module sont stockées dans une base PostgreSQL, dans le schéma `rbs`.
Les scripts sql se trouvent dans le dossier "src/main/resources/sql".

3 tables représentent le modèle relationnel applicatif :
 * `resource_type` : Types de ressources
 * `resource` : Ressources
 * `booking` : Réservations et créneaux de réservation
Une Réservation ponctuelle correspond à une entrée dans la table Booking
Une Réservation périodique correspond à une entrée mère dans la table `booking` représentant la réservation, et n entrées filles dans la table `booking` correspondant aux créneaux

Les tables `resource_type` et `resource` sont liées à des tables de partage pour implémenter le fonctionnement du framework Ent-core
 * `resource_type_shares`
 * `resource_shares`


## Modèle serveur

Le module serveur utilise 4 contrôleurs :
 * `DisplayController` : Routage des vues et sécurité globale
 * `ResourceTypeController` : APIs de manipulation des Types de ressources et sécurité sur ces objets
 * `ResourceController` : APIs de manipulation des Ressources et sécurité sur ces objets
 * `BookingController` : APIs de manipulation des Réservations et sécurité sur ces objets, contrôles particuliers sur le status des Réservations et les propriétés des Ressources et Types de ressources.

Les contrôleurs étendent les classes du framework Ent-core exploitant les CrudServices de base.
Pour manipulations spécifiques, des classes de Service sont utilisées :
 * `ResourceTypeService` : concernant les Types de ressources
 * `ResourceService` : concernant les Ressources
 * `BookingService` : concernant les Réservations

Le module serveur met en œuvre deux évènements issus du framework Ent-core :

* `RbsRepositoryEvents` : Logique de changement d'année scolaire
* `RbsSearchingEvents` : Logique de recherche

Des jsonschemas permettent de vérifier les données reçues par le serveur, ils se trouvent dans le dossier "src/main/resources/jsonschema".

## Modèle front-end

Le modèle Front-end manipule 3 objets model :
 * `ResourceType` comprenant une Collection d'objets `Resource` de ce type
 * `Resource` comprenant une Collection d'objets `Booking`, réservations faites sur cette ressource
 * `Booking` comprenant, si c'est une Réservation périodique, un tableau d'objets `Booking` représantant les créneaux.

Il y a 2 Collections globales :
 * `model.resourceTypes` : synchronisée depuis le serveur. Sa synchronisation entraîne celles des collections de `Resource` de chaque Type de ressources. Celle des collections de `Booking` de chaque Ressource est conditionnée par la sélection de la Ressource.
 * `model.bookings` : vide, manipulée par le contôleur pour représenter les Réservations affichées selon les ressources sélectionnées et d'autres critères. Cette collection mélange les réservations et créneaux de réservations périodiques pour permettres les actions sur sélections (via la méthode `selection()`)
 
