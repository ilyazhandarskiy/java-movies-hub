package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MoviesStore {
    private final Map<Integer, Movie> movies = new HashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(1);


    public Movie addMovie(Movie movie) {
        movie.setId(idCounter.getAndIncrement());
        movies.put(movie.getId(), movie);
        return movie;
    }

    public List<Movie> getAllMovies() {
        return new ArrayList<>(movies.values());
    }

    public List<Movie> getMoviesByYear(int year) {
        return movies.values().stream()
                .filter(movie -> movie.getYear() == year)
                .toList();
    }

    public Optional<Movie> getMovieById(int id) {
        return Optional.ofNullable(movies.get(id));
    }

    public void deleteMovieById(int id) {
        movies.remove(id);
    }

    public void deleteAllMovies() {
        movies.clear();
        idCounter.set(1);
    }
}