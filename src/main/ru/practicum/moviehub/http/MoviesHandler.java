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

    private static final Pattern YEAR_QUERY_PATTERN = Pattern.compile("(^|&)year=(?<year>\\d+)(&|$)");
    private static final Pattern MOVIE_ID_INT_PATTERN = Pattern.compile("^/movies/(\\d+)$");
    //регулярное выражение для определения строки - по ТЗ требуется проверить является ли ID числом и выдавать HTTP 400
    private static final Pattern MOVIE_FIRST_PATH_PATTERN = Pattern.compile("^/movies/([^/]*)$");

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
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        String query = ex.getRequestURI().getQuery();

        if (method.equals("GET")) {
            if (path.equals("/movies") && query == null) {
                return Endpoint.GET_ALL;
            }
            if (MOVIE_FIRST_PATH_PATTERN.matcher(path).matches() && query == null) {
                return Endpoint.GET_BY_ID;
            }
            if (path.equals("/movies")) {
                return Endpoint.GET_ALL_BY_YEAR;
            }
            return Endpoint.UNKNOWN;
        } else if (method.equals("POST") && path.equals("/movies")) {
            return Endpoint.POST;
        } else if (method.equals("DELETE") && MOVIE_FIRST_PATH_PATTERN.matcher(path).matches()) {
            return Endpoint.DELETE;
        }
        return Endpoint.UNKNOWN;
    }


    private void handleGetAll(HttpExchange ex) throws IOException {
        String json = gson.toJson(store.getAllMovies());
        sendJson(ex, 200, json);
    }

    private void handleGetById(HttpExchange ex) throws IOException {
        Matcher matcher = MOVIE_ID_INT_PATTERN.matcher(ex.getRequestURI().getPath());

        if (!matcher.find()) {
            sendJson(ex, 400, gson.toJson(new ErrorResponse("Некорректный ID")));
            return;
        }

        int id = Integer.parseInt(matcher.group(1));

        Optional<Movie> movie = store.getMovieById(id);

        if (movie.isEmpty()) {
            sendJson(ex, 404, gson.toJson(new ErrorResponse("Фильм не найден")));
            return;
        }

        String json = gson.toJson(movie.get());
        sendJson(ex, 200, json);
    }

    private void handleGetAllByYear(HttpExchange ex) throws IOException {
        String query = ex.getRequestURI().getQuery();

        Matcher matcher = YEAR_QUERY_PATTERN.matcher(query == null ? "" : query);

        if (!matcher.find()) {
            sendJson(ex, 400, gson.toJson(new ErrorResponse("Некорректный параметр запроса — 'year'")));
            return;
        }

        int year = Integer.parseInt(matcher.group("year"));

        String json = gson.toJson(store.getMoviesByYear(year));
        sendJson(ex, 200, json);
    }

    private void handlePost(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("application/json")) {
            sendJson(ex, 415, gson.toJson(new ErrorResponse("Некорректный Content-Type")));
            return;
        }

        Movie movie;
        try {
            movie = gson.fromJson(body, Movie.class);
        } catch (JsonSyntaxException e) {
            sendJson(ex, 400, gson.toJson(new ErrorResponse("Некорректный JSON синтаксис")));
            return;
        }

        //список деталей валидации
        List<String> errors;

        try {
            errors = validateMovieInput(movie);
        } catch (IllegalArgumentException e) {
            sendJson(ex, 400, gson.toJson(new ErrorResponse(e.getMessage())));
            return;
        }

        if (!errors.isEmpty()) {
            sendJson(ex, 422, gson.toJson(new ErrorResponse("Ошибка валидации", errors)));
            return;
        }

        Movie savedMovie = store.addMovie(movie);
        sendJson(ex, 201, gson.toJson(savedMovie));
    }

    private void handleDelete(HttpExchange ex) throws IOException {
        Matcher matcher = MOVIE_ID_INT_PATTERN.matcher(ex.getRequestURI().getPath());

        if (!matcher.find()) {
            sendJson(ex, 400, gson.toJson(new ErrorResponse("Некорректный ID")));
            return;
        }

        int id = Integer.parseInt(matcher.group(1));
        Optional<Movie> movie = store.getMovieById(Integer.parseInt(matcher.group(1)));

        if (movie.isEmpty()) {
            sendJson(ex, 404, gson.toJson(new ErrorResponse("Фильм не найден")));
            return;
        }

        store.deleteMovieById(id);
        sendNoContent(ex);
    }


    // валидация данных
    private List<String> validateMovieInput(Movie movie) {
        if (movie == null) {
            throw new IllegalArgumentException("Объект movie не должен быть пустым");
        }

        List<String> result = new ArrayList<>();

        int maxYear = LocalDate.now().getYear() + 1;

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
