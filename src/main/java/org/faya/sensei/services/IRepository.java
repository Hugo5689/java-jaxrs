package org.faya.sensei.services;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IRepository<T> {
    /**
     * Fetch all items from storage.
     *
     * @return The list of items.
     */
    Collection<T> get();

    /**
     * Fetch an item from storage by primary key.
     *
     * @param id The id of the item.
     * @return The item.
     */
    default Optional<T> get(int id) {
        return Optional.empty();
    }

    /**
     * Fetch an item from storage by unique key.
     *
     * @param key The unique key of the item.
     * @return The item.
     */
    default Optional<T> get(String key)  {
        return Optional.empty();
    }

    /**
     * Fetch an item from storage based on foreign key name.
     *
     * @param key The name of the foreign key.
     * @param value The query value of the foreign key.
     * @return The list of items.
     */
    default Collection<T> foreignGet(String key, String value)  {
        return List.of();
    }

    /**
     * Save an item to storage.
     *
     * @param item The item to create.
     * @return The saved item id.
     */
    int post(T item);

    /**
     * Update an item based on id.
     *
     * @param id The id of the item.
     * @param item The updated item.
     * @return The updated item.
     */
    Optional<T> put(int id, T item);

    /**
     * Remove an item from storage according to id.
     *
     * @param id The id of the item.
     * @return The removed item.
     */
    Optional<T> delete(int id);
}
