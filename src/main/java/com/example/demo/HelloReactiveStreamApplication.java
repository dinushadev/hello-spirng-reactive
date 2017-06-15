package com.example.demo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Stream;

@SpringBootApplication
public class HelloReactiveStreamApplication {

	public static void main(String[] args) {
		SpringApplication.run(HelloReactiveStreamApplication.class, args);
	}

	@Component
	public static class RouterHandler{
		private MovieService movieService;

		public RouterHandler(MovieService movieService) {
			this.movieService = movieService;
		}


		public Mono<ServerResponse> all(ServerRequest serverRequest) {
			return ServerResponse.ok().body(movieService.findAll(),Movie.class);
		}

		public Mono<ServerResponse> movie(ServerRequest serverRequest) {
			String movieId = serverRequest.pathVariable("movieId");
			return ServerResponse.ok().body(movieService.byId(movieId),Movie.class);
		}

		public Mono<ServerResponse> event(ServerRequest serverRequest) {
			String movieId = serverRequest.pathVariable("movieId");
			return ServerResponse.ok()
					.contentType(MediaType.TEXT_EVENT_STREAM)
					.body(movieService.streamTheStrem(movieId),MovieEvent.class);
		}
	}

	@Bean
	RouterFunction<?> route(RouterHandler routerHandler){
		return RouterFunctions.route(RequestPredicates.GET("/movies"),routerHandler::all)
				.andRoute(RequestPredicates.GET("/movies/{movieId}"),routerHandler::movie)
				.andRoute(RequestPredicates.GET("/movies/{movieId}/events"),routerHandler::event);
	}

	@Bean
	CommandLineRunner commandLineRunner(MovieRepo movieReop){
		return args -> {

			movieReop.deleteAll().subscribe(null,null,()->{
				Stream.of("FnF","Montham yem","G of galaxy","Fix","Monky")
						.forEach(movie -> movieReop.save(new Movie(UUID.randomUUID().toString(),movie))
								.subscribe(ar -> System.out.println(ar.toString())));
			});
			movieReop.findAll().subscribe(System.out::print);

		};
	}



}

interface MovieRepo extends ReactiveMongoRepository<Movie,String> {

}

//@RestController
//@RequestMapping("/")
class MovieController{

	private final MovieService  movieService;


	public MovieController(MovieService movieService) {
		this.movieService = movieService;
	}

	@GetMapping("/movies/{movieId}")
	public Mono<Movie> getMovie(@PathVariable String movieId){
		return movieService.byId(movieId);
	}
	@GetMapping("/movies")
	public Flux<Movie> getAllMovie(){
		return movieService.findAll();
	}

	@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE, value = "/movies/{movieId}/event")
	public Flux<MovieEvent> getMovieEvents(@PathVariable String movieId){
		return movieService.streamTheStrem(movieId);
	}
}

@Service
class MovieService {

	private final MovieRepo movieRepo;

	public MovieService(MovieRepo movieRepo) {
		this.movieRepo = movieRepo;
	}

	public Mono<Movie> byId(String movieId){
		return movieRepo.findById(movieId);
	}

	public Flux<Movie> findAll(){
		return movieRepo.findAll();
	}

	public Flux<MovieEvent> streamTheStrem(String id){
		return byId(id).flatMapMany(movie -> {
			Flux<Long> interval = Flux.interval(Duration.ofSeconds(1));
			Flux<MovieEvent> movieEventFlux = Flux.fromStream(Stream.generate(() -> new MovieEvent(movie, new Date())));
			return Flux.zip(interval,movieEventFlux).map(Tuple2::getT2);
		});

	}

}

@Data
@AllArgsConstructor
class MovieEvent{
	private Movie movie;
	private Date date;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document
class Movie{

	@Id
	private String id;
	private String title;

}