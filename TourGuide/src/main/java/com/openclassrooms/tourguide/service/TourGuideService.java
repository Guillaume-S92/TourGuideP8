package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.dto.NearbyAttractionDTO;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService {
    private Logger logger = LoggerFactory.getLogger(TourGuideService.class);

    private final GpsUtil gpsUtil;
    private final RewardsService rewardsService;
    private final TripPricer tripPricer = new TripPricer();

    // Dedicated pool for async tasks (CompletableFuture)
    private final ExecutorService executor = Executors.newFixedThreadPool(100);

    public final Tracker tracker;
    boolean testMode = true;

    public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
        this.gpsUtil = gpsUtil;
        this.rewardsService = rewardsService;

        Locale.setDefault(Locale.US);

        if (testMode) {
            logger.info("TestMode enabled");
            logger.debug("Initializing users");
            initializeInternalUsers();
            logger.debug("Finished initializing users");
        }
        tracker = new Tracker(this);
        addShutDownHook();
    }

    public List<UserReward> getUserRewards(User user) {
        return user.getUserRewards();
    }

    public VisitedLocation getUserLocation(User user) {
        VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0)
                ? user.getLastVisitedLocation()
                : trackUserLocation(user);
        return visitedLocation;
    }

    public User getUser(String userName) {
        return internalUserMap.get(userName);
    }

    public List<User> getAllUsers() {
        return internalUserMap.values().stream().collect(Collectors.toList());
    }

    public void addUser(User user) {
        // simple atomic insert
        internalUserMap.putIfAbsent(user.getUserName(), user);
    }

    public List<Provider> getTripDeals(User user) {
        int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(UserReward::getRewardPoints).sum();
        List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
                user.getUserPreferences().getNumberOfAdults(),
                user.getUserPreferences().getNumberOfChildren(),
                user.getUserPreferences().getTripDuration(),
                cumulatativeRewardPoints);
        user.setTripDeals(providers);
        return providers;
    }

    public VisitedLocation trackUserLocation(User user) {
        VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
        user.addToVisitedLocations(visitedLocation);
        rewardsService.calculateRewards(user);
        return visitedLocation;
    }

    // CompletableFuture version (producer)
    public CompletableFuture<VisitedLocation> trackUserLocationAsync(User user) {
        return CompletableFuture.supplyAsync(() -> trackUserLocation(user), executor);
    }

    // Run tracking for ALL users concurrently (consumer waits with allOf)
    public void trackAllUsersLocationsAsyncAndWait() {
        List<User> users = getAllUsers();
        CompletableFuture<?>[] futures = users.stream()
                .map(this::trackUserLocationAsync)
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).join();
    }
    // CompletableFuture version for rewards calculation
    public CompletableFuture<Void> calculateRewardsAsync(User user) {
        return CompletableFuture.runAsync(() -> rewardsService.calculateRewards(user), executor);
    }

    // Run rewards calculation for ALL users concurrently and wait
    public void calculateRewardsForAllUsersAsyncAndWait() {
        List<User> users = getAllUsers();

        CompletableFuture<?>[] futures = users.stream()
                .map(this::calculateRewardsAsync)
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures).join();
    }
    // Used by step 3 test: always return 5 closest (no range filter)
    public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
        return gpsUtil.getAttractions().stream()
                .sorted(Comparator.comparingDouble(a -> rewardsService.getDistance(a, visitedLocation.location)))
                .limit(5)
                .toList();
    }

    private void addShutDownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            tracker.stopTracking();
            executor.shutdownNow();
        }));
    }

    @PreDestroy
    public void shutdown() {
        tracker.stopTracking();
        executor.shutdownNow();
    }

    /**********************************************************************************
     *
     * Methods Below: For Internal Testing
     *
     **********************************************************************************/
    private static final String tripPricerApiKey = "test-server-api-key";

    // internal users stored in memory
    private final Map<String, User> internalUserMap = new ConcurrentHashMap<>();

    private void initializeInternalUsers() {
        IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
            String userName = "internalUser" + i;
            String phone = "000";
            String email = userName + "@tourGuide.com";
            User user = new User(UUID.randomUUID(), userName, phone, email);
            generateUserLocationHistory(user);

            internalUserMap.put(userName, user);
        });
        logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
    }

    private void generateUserLocationHistory(User user) {
        IntStream.range(0, 3).forEach(i -> user.addToVisitedLocations(new VisitedLocation(
                user.getUserId(),
                new Location(generateRandomLatitude(), generateRandomLongitude()),
                getRandomTime()
        )));
    }

    private double generateRandomLongitude() {
        double leftLimit = -180;
        double rightLimit = 180;
        return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
    }

    private double generateRandomLatitude() {
        double leftLimit = -85.05112878;
        double rightLimit = 85.05112878;
        return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
    }

    private Date getRandomTime() {
        LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
        return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
    }

    // Endpoint helper: top 5 DTOs sorted by distance
    public List<NearbyAttractionDTO> getTopFiveNearbyAttractions(User user) {
        VisitedLocation visitedLocation = getUserLocation(user);

        return gpsUtil.getAttractions().stream()
                .sorted(Comparator.comparingDouble(a -> rewardsService.getDistance(a, visitedLocation.location)))
                .limit(5)
                .map(a -> {
                    double distance = rewardsService.getDistance(a, visitedLocation.location);
                    int points = rewardsService.getRewardPoints(a, user);
                    return new NearbyAttractionDTO(
                            a.attractionName,
                            a.latitude,
                            a.longitude,
                            visitedLocation.location.latitude,
                            visitedLocation.location.longitude,
                            distance,
                            points
                    );
                })
                .toList();
    }
}