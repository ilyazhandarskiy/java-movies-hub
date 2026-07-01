package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MoviesHandler extends BaseHttpHandler {
    private static final int MAX_TITLE_SIZE = 100;
    private static final int MIN_YEAR = 1888;

    private final MoviesStore store;
    private final Gson gson = new Gson();

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        Endpoint endpoint = getEndpoint(ex);

        switch (endpoint) {
            case GET_ALL:
                handleGetAll(ex);
                break;
            case GET_BY_ID:
                handleGetById(ex);
                break;
            case POST:
                handlePost(ex);
                break;
            case GET_ALL_BY_YEAR:
                handleGetAllByYear(ex);
                break;
            case DELETE:
                handleDelete(ex);
                break;
            case UNKNOWN:
                sendMethodNotAllowed(ex);
                break;
        }
    }

    private Endpoint getEndpoint(HttpExchange ex) {
        if (ex.getRequestMethod().equals("GET")) {
            if (ex.getRequestURI().getPath().equals("/movies") && ex.getRequestURI().getQuery() == null) {
                return Endpoint.GET_ALL;
            }
            if (ex.getRequestURI().getPath().contains("/movies/") && ex.getRequestURI().getQuery() == null) {
                return Endpoint.GET_BY_ID;
            }
            if (ex.getRequestURI().getPath().equals("/movies") && ex.getRequestURI().getQuery() != null) {
                return Endpoint.GET_ALL_BY_YEAR;
            }
            return Endpoint.UNKNOWN;
        } else if (ex.getRequestMethod().equals("POST") && ex.getRequestURI().getPath().equals("/movies")) {
            return Endpoint.POST;
        } else if (ex.getRequestMethod().equals("DELETE") && ex.getRequestURI().getPath().contains("/movies/")) {
            return Endpoint.DELETE;
        }
        return Endpoint.UNKNOWN;
    }


    private void handleGetAll(HttpExchange ex) throws IOException {
        String json = gson.toJson(store.getAllMovies());
        sendJson(ex, 200, json);
    }

    private void handleGetById(HttpExchange ex) throws IOException {
        int id;

        Pattern pattern = Pattern.compile("^/movies/(\\d+)$");
        Matcher matcher = pattern.matcher(ex.getRequestURI().getPath());

        if (matcher.find()) {
            id = Integer.parseInt(matcher.group(1));
        } else {
            sendJson(ex, 400, gson.toJson(new ErrorResponse("Некорректный ID")));
            return;
        }

        Optional<Movie> movie = store.getMovieById(id);

        if (movie.isEmpty()) {
            sendJson(ex, 404, gson.toJson(new ErrorResponse("Фильм не найден")));
            return;
        }

        String json = gson.toJson(movie.get());
        sendJson(ex, 200, json);
    }

    private void handleGetAllByYear(HttpExchange ex) throws IOException {
        int year;
        String query = ex.getRequestURI().getQuery();

        Pattern pattern = Pattern.compile("(^|&)year=(?<year>\\d+)(&|$)");
        Matcher matcher = pattern.matcher(query);

        if (matcher.find()) {
            year = Integer.parseInt(matcher.group("year"));
        } else {
            sendJson(ex, 400, gson.toJson(new ErrorResponse("Некорректный параметр запроса — 'year'")));
            return;
        }

        String json = gson.toJson(store.getMoviesByYear(year));
        sendJson(ex, 200, json);
    }

    private void handlePost(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("application/json")) {
            sendJson(ex, 415, gson.toJson(new ErrorResponse("Invalid Content-Type")));
            return;
        }

        Movie movie;
        try {
            movie = gson.fromJson(body, Movie.class);
        } catch (JsonSyntaxException e) {
            sendJson(ex, 400, gson.toJson(new ErrorResponse("Invalid JSON syntax")));
            return;
        }

        List<String> errors = validateMovieInput(movie);

        if (!errors.isEmpty()) {
            sendJson(ex, 422, gson.toJson(new ErrorResponse("Ошибка валидации", errors)));
            return;
        }

        Movie savedMovie = store.addMovie(movie);
        sendJson(ex, 201, gson.toJson(savedMovie));
    }

    private void handleDelete(HttpExchange ex) throws IOException {
        int id;

        Pattern pattern = Pattern.compile("^/movies/(\\d+)$");
        Matcher matcher = pattern.matcher(ex.getRequestURI().getPath());

        if (matcher.find()) {
            id = Integer.parseInt(matcher.group(1));
        } else {
            sendJson(ex, 400, gson.toJson(new ErrorResponse("Некорректный ID")));
            return;
        }

        Optional<Movie> movie = store.getMovieById(id);

        if (movie.isEmpty()) {
            sendJson(ex, 404, gson.toJson(new ErrorResponse("Фильм не найден")));
            return;
        }

        store.deleteMovieById(id);
        sendNoContent(ex);
    }


    // валидация данных
    private List<String> validateMovieInput(Movie movie) {
        int maxYear = LocalDate.now().getYear() + 1;

        List<String> result = new ArrayList<>();

        if (movie.getTitle() == null || movie.getTitle().isBlank()) {
            result.add("название не должно быть пустым");
        } else if (movie.getTitle().length() > MAX_TITLE_SIZE) {
            result.add("название не должно быть больше " + MAX_TITLE_SIZE + " символов");
        }

        if (movie.getYear() < MIN_YEAR || movie.getYear() > maxYear) {
            result.add("год должен быть между " + MIN_YEAR + " и " + maxYear);
        }

        return result;
    }
}
