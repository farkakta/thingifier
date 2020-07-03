package uk.co.compendiumdev.thingifier.query;

import uk.co.compendiumdev.thingifier.Thing;
import uk.co.compendiumdev.thingifier.Thingifier;
import uk.co.compendiumdev.thingifier.generic.definitions.RelationshipVector;
import uk.co.compendiumdev.thingifier.generic.definitions.ThingDefinition;
import uk.co.compendiumdev.thingifier.generic.instances.ThingInstance;

import java.util.ArrayList;
import java.util.List;

import static uk.co.compendiumdev.thingifier.query.SimpleQuery.LastMatchValue.*;

final public class SimpleQuery {

    private final Thingifier thingifier;
    private final String query;

    private boolean isCollection = false;
    private boolean pluralMatch = false;

    public boolean isResultACollection() {
        return isCollection;
    }

    enum LastMatchValue {NOTHING, CURRENT_THING, CURRENT_INSTANCE, CURRENT_ITEMS, CURRENT_RELATIONSHIP}

    ;

    LastMatchValue lastMatch = NOTHING;

    // populated during search
    Thing currentThing = null;
    ThingInstance currentInstance = null;
    List<ThingInstance> foundItems = new ArrayList<ThingInstance>();
    RelationshipVector lastRelationshipFound = null;
    List<RelationshipVector> lastRelationshipsFound = null;
    Thing parentThing = null;
    private ThingInstance parentInstance = null;

    // TODO: this should be an object with FoundItem objects which have getAsRelationshipDefinition etc.
    List<Object> foundItemsHistoryList = new ArrayList<>();
    private ThingDefinition resultContainsDefinition;

    public SimpleQuery(Thingifier thingifier, String query) {
        this.thingifier = thingifier;
        this.query = query;
    }


    public SimpleQuery performQuery() {
        // a simple query is a URL based REST query
        // e.g. THING/_GUID_/RELATIONSHIP/THING
        // THING/RELATIONSHIP

        String[] terms = query.split("/");


        lastMatch = NOTHING;

        for (String term : terms) {

            // if we have a parent thing then we want to check for relationships before we check for things
            // if it matches a relationship then get the instances identified by the relationship
            //if(currentThing != null && currentThing.definition().hasRelationship(term)){
            if (parentThing!=null && thingifier.hasRelationshipNamed(term)) {

                // what I want to store is the relationship between the parent Thing and the relationship name
                Thing thingToCheckForRelationship = currentThing == null ? parentThing : currentThing;
                lastRelationshipsFound = thingToCheckForRelationship.definition().getRelationships(term);
                lastRelationshipFound = lastRelationshipsFound.get(0);

                foundItemsHistoryList.add(lastRelationshipFound);


                if (foundItems != null && foundItems.size() > 0) {
                    resultContainsDefinition = foundItems.get(0).typeOfConnectedItems(term);
                }

                List<ThingInstance> newitems = new ArrayList<ThingInstance>();
                if (foundItems != null) {
                    for (ThingInstance instance : foundItems) {
                        newitems.addAll(instance.connectedItems(term));
                    }
                }

                // relationships is always a collection
                isCollection = true;

                foundItems = newitems;
                parentInstance = currentInstance;
                parentThing = currentThing;
                currentThing = null;
                currentInstance = null;
                lastMatch = CURRENT_RELATIONSHIP;
                continue;
            }

            // if matches an entity type
            if (thingifier.hasThingNamed(term) || thingifier.hasThingWithPluralNamed(term)) {
                if (currentThing == null && foundItems.size() == 0) {
                    // first thing - find it
                    currentThing = thingifier.getThingNamed(term);
                    pluralMatch = false;

                    if (currentThing == null) {
                        // was it the plural?
                        currentThing = thingifier.getThingWithPluralNamed(term);
                        pluralMatch = true;
                    }

                    // entity type is always a collection
                    isCollection = true;

                    resultContainsDefinition = currentThing.definition();
                    foundItemsHistoryList.add(currentThing);
                    parentThing = currentThing;
                    currentInstance = null;
                    lastMatch = CURRENT_THING;
                    foundItems = new ArrayList<ThingInstance>(currentThing.getInstances());

                } else {
                    // related to another type of thing
                    foundItemsHistoryList.add(thingifier.getThingNamed(term));

                    if (foundItems != null && foundItems.size() > 0) {
                        resultContainsDefinition = foundItems.get(0).typeOfConnectedItems(term);
                    }

                    List<ThingInstance> newitems = new ArrayList<ThingInstance>();
                    if (foundItems != null) {
                        for (ThingInstance instance : foundItems) {
                            List<ThingInstance> matchedInstances = instance.connectedItemsOfType(term);
                            newitems.addAll(matchedInstances);
                        }
                    }

                    // relationship is a collection
                    foundItems = newitems;
                    lastMatch = CURRENT_ITEMS;
                    parentThing = currentThing;
                    currentThing = null;
                    currentInstance = null;
                }
                continue;
            }



            // is it a GUID?
            boolean found = false;
            for (ThingInstance instance : foundItems) {
                if (instance.getGUID().contentEquals(term)) {

                    foundItemsHistoryList.add(instance);

                    if (currentThing != null) {
                        parentThing = currentThing;
                    }

                    // if we had a plural term then return this as a collection
                    isCollection = pluralMatch;

                    currentThing = null;

                    currentInstance = instance;
                    foundItems = new ArrayList<ThingInstance>();
                    foundItems.add(instance);
                    lastMatch = CURRENT_INSTANCE;
                    found = true;
                }
                if (found) {
                    break;
                }
            }
            if (found) {
                // it was a GUID
                continue;
            }

            // is it a field?
            // is it a filter query?  e.g. ?title="name"
            lastMatch = NOTHING;
        }

        return this;
    }

    public List<ThingInstance> getListThingInstance() {
        List<ThingInstance> returnThis = new ArrayList<ThingInstance>();

        if (lastMatch == CURRENT_THING) {
            returnThis.addAll(currentThing.getInstances());
        }

        if (lastMatch == CURRENT_INSTANCE) {
            returnThis.add(currentInstance);
        }

        if (lastMatch == CURRENT_ITEMS || lastMatch == CURRENT_RELATIONSHIP) {
            returnThis.addAll(foundItems);
        }

        //if(lastMatch==NOTHING){ // then the array is already empty}

        return returnThis;
    }

    public boolean lastMatchWasRelationship() {
        return lastMatch == CURRENT_RELATIONSHIP;
    }

    public String getLastRelationshipName() {
        return lastRelationshipFound.getName();
    }

    public ThingInstance getParentInstance() {
        return parentInstance;
    }

    public boolean wasItemFoundUnderARelationship() {

        // not enough in the history list to do the check, so no it wasn't
        if (foundItemsHistoryList.size() - 2 < 0) {
            return false;
        }
        return foundItemsHistoryList.get(foundItemsHistoryList.size() - 2) instanceof RelationshipVector;
    }

    public ThingInstance getLastInstance() {
        return currentInstance;
    }

    public boolean lastMatchWasInstance() {
        return lastMatch == CURRENT_INSTANCE;
    }

    public boolean lastMatchWasNothing() {
        return lastMatch == NOTHING;
    }

    public ThingDefinition resultContainsDefn() {
        return resultContainsDefinition;
    }
}