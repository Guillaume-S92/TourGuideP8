# TourGuide P8 - Optimisation de performance et intégration continue

Projet réalisé dans le cadre du parcours **Développeur d'application Java** d'OpenClassrooms.

TourGuide est une application **Spring Boot** permettant à des utilisateurs de localiser des attractions touristiques proches, de consulter leurs récompenses et d'obtenir des offres de voyage personnalisées.  
Ce projet avait pour objectif principal de rendre l'application plus performante afin de supporter une forte montée en charge, tout en fiabilisant les tests et en ajoutant un pipeline d'intégration continue.

---

## Sommaire

- [Contexte du projet](#contexte-du-projet)
- [Objectifs](#objectifs)
- [Fonctionnalités principales](#fonctionnalités-principales)
- [Améliorations réalisées](#améliorations-réalisées)
- [Architecture](#architecture)
- [Technologies utilisées](#technologies-utilisées)
- [Prérequis](#prérequis)
- [Installation](#installation)
- [Lancement de l'application](#lancement-de-lapplication)
- [Endpoints disponibles](#endpoints-disponibles)
- [Tests](#tests)
- [Pipeline d'intégration continue](#pipeline-dintégration-continue)
- [Limites et axes d'amélioration](#limites-et-axes-damélioration)
- [Auteur](#auteur)

---

## Contexte du projet

L'application TourGuide a connu une forte croissance du nombre d'utilisateurs.  
Elle devait passer d'un usage limité à une volumétrie pouvant atteindre **100 000 utilisateurs**.

Dans sa version initiale, certains traitements étaient trop lents, notamment :

- la récupération des positions utilisateurs via **gpsUtil** ;
- le calcul des récompenses via **RewardsCentral** ;
- la génération des recommandations d'attractions ;
- certains tests unitaires qui échouaient de manière intermittente.

Le projet consistait donc à optimiser l'application existante sans en changer le besoin métier principal.

---

## Objectifs

Les objectifs principaux du projet étaient les suivants :

- corriger les tests unitaires instables ;
- corriger la recommandation des attractions touristiques ;
- retourner les **5 attractions les plus proches** de l'utilisateur, quelle que soit la distance ;
- améliorer les performances de récupération des localisations utilisateurs ;
- améliorer les performances de calcul des récompenses ;
- conserver des tests de performance permettant de vérifier les gains obtenus ;
- mettre en place une chaîne d'intégration continue capable de compiler, tester et produire le fichier `.jar`.

---

## Fonctionnalités principales

L'application permet de :

- récupérer la dernière position connue d'un utilisateur ;
- calculer les attractions touristiques les plus proches ;
- retourner les informations utiles pour chaque attraction recommandée :
  - nom de l'attraction ;
  - coordonnées GPS de l'attraction ;
  - coordonnées GPS de l'utilisateur ;
  - distance entre l'utilisateur et l'attraction ;
  - points de récompense associés ;
- calculer les récompenses d'un utilisateur ;
- récupérer des offres de voyage personnalisées via **tripPricer**.

---

## Améliorations réalisées

### Correction des recommandations d'attractions

La méthode de recommandation a été adaptée afin de retourner systématiquement les **5 attractions les plus proches** de l'utilisateur.

Avant correction, les recommandations pouvaient être absentes ou limitées par une logique de distance trop restrictive.  
Après correction, l'application trie les attractions par distance et retourne les 5 premières, même si elles sont éloignées.

---

### Optimisation de la récupération des localisations

La récupération des positions utilisateurs a été optimisée avec un traitement asynchrone basé sur :

- `CompletableFuture` ;
- un `ExecutorService` dédié ;
- une exécution concurrente des appels à `gpsUtil`.

L'objectif est de traiter un grand volume d'utilisateurs beaucoup plus rapidement qu'avec une exécution strictement séquentielle.

#### Variante avec les Virtual Threads

Une implémentation alternative est disponible sur la branche [`virtual-threads`](https://github.com/Guillaume-S92/TourGuideP8/tree/virtual-threads). Elle utilise les **Virtual Threads de Java 21** à la place de `CompletableFuture` et du pool fixe de threads.

Les deux branches permettent ainsi d'implémenter et de comparer les deux technologies avec les mêmes traitements et les mêmes tests de performance :

- `master` : `CompletableFuture` avec un `ExecutorService` dédié ;
- `virtual-threads` : un virtual thread par tâche avec `Executors.newVirtualThreadPerTaskExecutor()`.

---

### Optimisation du calcul des récompenses

Le calcul des récompenses a été amélioré pour limiter les traitements inutiles.

Les principales optimisations sont :

- utilisation d'une collection de type `Set` pour vérifier rapidement les attractions déjà récompensées ;
- arrêt anticipé lorsque toutes les attractions ont déjà été traitées ;
- parallélisation du calcul des récompenses pour l'ensemble des utilisateurs.

---

### Fiabilisation des tests

Les tests unitaires instables ont été corrigés afin d'obtenir une base de validation plus fiable.

Le projet contient également des tests de performance permettant de vérifier que les optimisations restent compatibles avec les objectifs attendus :

- récupération de la localisation de nombreux utilisateurs ;
- calcul des récompenses de nombreux utilisateurs ;
- validation des seuils de temps imposés.

---

### Mise en place de l'intégration continue

Un pipeline **GitHub Actions** a été ajouté au projet.

Il permet de :

- récupérer le projet ;
- installer les dépendances locales nécessaires ;
- compiler l'application ;
- exécuter les tests unitaires ;
- construire le `.jar` exécutable ;
- publier l'artefact généré.

---

## Architecture

Architecture simplifiée de l'application :

```text
Utilisateur / Client HTTP
        |
        v
TourGuideController
        |
        v
TourGuideService
        |
        +--> gpsUtil
        |       - récupération des localisations utilisateurs
        |       - récupération des attractions touristiques
        |
        +--> RewardsService
        |       - calcul des récompenses
        |       - appel à RewardsCentral
        |
        +--> tripPricer
                - récupération des offres de voyage
```

### Rôle des principaux composants

| Composant | Rôle |
|---|---|
| `TourGuideController` | Expose les endpoints REST de l'application |
| `TourGuideService` | Contient la logique principale : localisation, attractions, offres de voyage |
| `RewardsService` | Calcule les récompenses liées aux attractions visitées |
| `gpsUtil` | Librairie externe simulant la récupération des positions et attractions |
| `RewardsCentral` | Librairie externe permettant de calculer les points de récompense |
| `tripPricer` | Librairie externe permettant de récupérer des offres de voyage |
| `Tracker` | Composant chargé du suivi périodique des positions utilisateurs |

---

## Technologies utilisées

- **Java 17**
- **Spring Boot 3.1.1**
- **Maven**
- **JUnit 5**
- **GitHub Actions**
- **gpsUtil**
- **RewardsCentral**
- **tripPricer**

---

## Prérequis

Avant de lancer le projet, il faut avoir installé :

- Java 17 ou une version supérieure ;
- Maven, ou utiliser le wrapper Maven fourni avec le projet ;
- Git ;
- les librairies locales présentes dans le dossier `TourGuide/libs`.

> Le pipeline GitHub Actions utilise un JDK 21, mais le projet est configuré avec `java.version=17`.

---

## Installation

Cloner le repository :

```bash
git clone https://github.com/Guillaume-S92/TourGuideP8.git
cd TourGuideP8/TourGuide
```

Installer les dépendances locales dans le repository Maven local :

```bash
mvn install:install-file -Dfile=libs/gpsUtil.jar -DgroupId=gpsUtil -DartifactId=gpsUtil -Dversion=1.0.0 -Dpackaging=jar

mvn install:install-file -Dfile=libs/RewardCentral.jar -DgroupId=rewardCentral -DartifactId=rewardCentral -Dversion=1.0.0 -Dpackaging=jar

mvn install:install-file -Dfile=libs/TripPricer.jar -DgroupId=tripPricer -DartifactId=tripPricer -Dversion=1.0.0 -Dpackaging=jar
```

Sur Windows, il est aussi possible d'utiliser :

```bash
mvnw.cmd clean install
```

Sur Linux ou macOS :

```bash
./mvnw clean install
```

---

## Lancement de l'application

Lancer l'application avec Maven :

```bash
mvn spring-boot:run
```

Ou construire puis lancer le fichier `.jar` :

```bash
mvn clean package
java -jar target/tourguide-0.0.1-SNAPSHOT.jar
```

L'application est ensuite accessible à l'adresse suivante :

```text
http://localhost:8080
```

---

## Endpoints disponibles

| Méthode | Endpoint | Description |
|---|---|---|
| `GET` | `/` | Vérifie que l'application est disponible |
| `GET` | `/getLocation?userName=internalUser0` | Récupère la dernière position connue d'un utilisateur |
| `GET` | `/getNearbyAttractions?userName=internalUser0` | Retourne les 5 attractions les plus proches |
| `GET` | `/getRewards?userName=internalUser0` | Retourne les récompenses de l'utilisateur |
| `GET` | `/getTripDeals?userName=internalUser0` | Retourne les offres de voyage disponibles |

Exemple :

```bash
curl "http://localhost:8080/getNearbyAttractions?userName=internalUser0"
```

---

## Tests

### Lancer tous les tests unitaires

```bash
mvn test
```

### Lancer les tests sans les tests de performance

```bash
mvn test -Dtest='!*TestPerformance*'
```

### Lancer uniquement les tests de performance

```bash
mvn test -Dtest=TestPerformance
```

Les tests de performance permettent de vérifier que les optimisations répondent aux contraintes suivantes :

| Traitement | Objectif |
|---|---|
| Récupération des localisations utilisateurs | 100 000 utilisateurs en moins de 15 minutes |
| Calcul des récompenses utilisateurs | 100 000 utilisateurs en moins de 20 minutes |

---

## Pipeline d'intégration continue

Le projet contient un workflow GitHub Actions dans :

```text
.github/workflows/ci.yml
```

Le pipeline est déclenché sur les branches :

- `main`
- `master`
- `stape-5`

Le workflow exécute les étapes suivantes :

1. checkout du repository ;
2. installation du JDK ;
3. installation des librairies locales `gpsUtil`, `TripPricer` et `RewardCentral` ;
4. compilation du projet ;
5. exécution des tests unitaires ;
6. packaging du projet ;
7. publication du fichier `.jar` en artefact GitHub Actions.

Les tests de performance sont volontairement exclus du pipeline principal afin d'éviter un temps d'exécution trop long à chaque push ou pull request.

---

## Limites et axes d'amélioration

Quelques améliorations pourraient être envisagées pour aller plus loin :

- publier les librairies `gpsUtil`, `RewardCentral` et `tripPricer` dans un vrai repository Maven ;
- rendre la taille du pool de threads configurable ;
- ajouter un profil dédié pour les tests de performance lourds ;
- ajouter une mesure automatisée de couverture de tests ;
- ajouter un quality gate avec SonarCloud ou un outil équivalent ;
- externaliser davantage les paramètres techniques de l'application.

---

## Auteur

Projet réalisé par **Guillaume S.**

Repository final :  
<https://github.com/Guillaume-S92/TourGuideP8>

Repository de base fourni par OpenClassrooms :  
<https://github.com/OpenClassrooms-Student-Center/JavaPathENProject8>
