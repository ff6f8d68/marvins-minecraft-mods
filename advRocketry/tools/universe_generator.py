#!/usr/bin/env python3
"""
universe_generator.py - Fetches real NASA exoplanet data and nearby Gaia stars,
then outputs a universe_data.json compatible with the advRocketry mod.

Run: python3 tools/universe_generator.py
Output: src/main/resources/data/adv_rocketry/universe_data.json

This JSON is loaded by NasaUniverseLoader.java on server start to populate
the galaxy with real star systems and procedurally-generated planets.
"""


import json
import math
import random
import hashlib
import urllib.parse
import urllib.request
import sys
import time
from pathlib import Path

# --- Planet Type Enums (must match Java constants) ---
TYPE_ROCKY = 0
TYPE_EARTH_LIKE = 1
TYPE_VENUS_LIKE = 2
TYPE_GAS_GIANT = 3
TYPE_ICE_GIANT = 4
TYPE_LAVA = 5
TYPE_ICE_WORLD = 6

# --- Fluid Type Enums ---
FLUID_NONE = 0
FLUID_WATER = 1
FLUID_METHANE = 2
FLUID_LAVA = 3

# --- Surface Palette Enums ---
PALETTE_STANDARD = 0
PALETTE_RUST = 1
PALETTE_FROZEN = 2
PALETTE_SCORCHED = 3
PALETTE_ICE_SHEET = 4

# Scale: how many mod-units per parsec
# 1 AU = 1 mod-unit (matching the mod's convention)
# 1 parsec = 206265 AU, but we scale down for manageable numbers
# Using 1 parsec = 200000 AU so positions are in realistic AU
PARSECS_TO_AU = 206265.0

# Max nearby stars from Gaia to include
MAX_GAIA_STARS = 500

# Max planets per system (for simulated Gaia stars)
MAX_SIMULATED_PLANETS = 6


def seed_from_name(name):
    """Deterministic hash of a name to use as RNG seed."""
    return int(hashlib.md5(name.encode()).hexdigest()[:8], 16)


def star_type_from_temp(temp):
    """
    Classify star spectral type from temperature.
    Used to determine star color tint.
    """
    if temp > 30000:
        return "O"
    elif temp > 10000:
        return "B"
    elif temp > 7500:
        return "A"
    elif temp > 6000:
        return "F"
    elif temp > 5200:
        return "G"
    elif temp > 3700:
        return "K"
    else:
        return "M"


def star_color_from_temp(temp):
    """Return (r, g, b) color tuple for a star based on its temperature."""
    if temp > 30000:
        return (0.6, 0.7, 1.0)
    elif temp > 10000:
        return (0.7, 0.8, 1.0)
    elif temp > 7500:
        return (0.9, 0.9, 1.0)
    elif temp > 6000:
        return (1.0, 1.0, 0.9)
    elif temp > 5200:
        return (1.0, 0.95, 0.8)
    elif temp > 3700:
        return (1.0, 0.7, 0.4)
    else:
        return (1.0, 0.5, 0.3)


def classify_planet(radius_earth, mass_earth, eq_temp, density):
    """Classify a planet into a type based on its physical properties."""
    if radius_earth > 4.0:
        return TYPE_GAS_GIANT
    elif radius_earth > 1.7:
        return TYPE_ICE_GIANT
    elif eq_temp > 700:
        return TYPE_LAVA
    elif eq_temp < 180:
        return TYPE_ICE_WORLD
    elif 230 <= eq_temp <= 320 and 0.8 <= radius_earth <= 1.5:
        return TYPE_EARTH_LIKE
    elif eq_temp > 350 and density > 4.0:
        return TYPE_VENUS_LIKE
    else:
        return TYPE_ROCKY


def classify_fluid_and_palette(eq_temp, planet_type):
    """Determine fluid type and surface palette from temperature."""
    if eq_temp < 90:
        return FLUID_METHANE, PALETTE_FROZEN
    elif 273 <= eq_temp <= 373:
        return FLUID_WATER, PALETTE_STANDARD
    elif eq_temp > 373:
        return FLUID_LAVA, PALETTE_SCORCHED
    elif eq_temp < 180:
        return FLUID_NONE, PALETTE_ICE_SHEET
    else:
        return FLUID_NONE, PALETTE_STANDARD


def generate_atmosphere(planet_type, eq_temp, radius_earth, density, rng):
    """
    Generate a plausible atmosphere composition.
    Returns dict of {gas_name: {in_atm, liquid, frozen_surface, frozen_deep}}
    """
    atm = {}

    if planet_type == TYPE_GAS_GIANT:
        atm["hydrogen"] = {"in_atm": round(rng.uniform(2, 8), 4), "liquid": 0, "frozen_surface": 0, "frozen_deep": 0}
        atm["helium_proxy"] = {"in_atm": round(rng.uniform(0.5, 2), 4), "liquid": 0, "frozen_surface": 0, "frozen_deep": 0}
        atm["methane"] = {"in_atm": round(rng.uniform(0, 0.3), 4), "liquid": 0, "frozen_surface": 0, "frozen_deep": 0}
        return atm

    if planet_type == TYPE_ICE_GIANT:
        atm["hydrogen"] = {"in_atm": round(rng.uniform(1, 4), 4), "liquid": 0, "frozen_surface": 0, "frozen_deep": 0}
        atm["methane"] = {"in_atm": round(rng.uniform(0.1, 1), 4), "liquid": 0, "frozen_surface": 0, "frozen_deep": 0}
        atm["water"] = {"in_atm": 0, "liquid": 0, "frozen_surface": round(rng.uniform(0.5, 3), 4), "frozen_deep": 0}
        return atm

    if planet_type == TYPE_LAVA:
        atm["co2"] = {"in_atm": round(rng.uniform(0.5, 5), 4), "liquid": 0, "frozen_surface": 0, "frozen_deep": 0}
        atm["nitrogen"] = {"in_atm": round(rng.uniform(0.01, 0.5), 4), "liquid": 0, "frozen_surface": 0, "frozen_deep": 0}
        atm["so2_proxy"] = {"in_atm": round(rng.uniform(0.01, 0.2), 4), "liquid": 0, "frozen_surface": 0, "frozen_deep": 0}
        return atm

    if planet_type == TYPE_ICE_WORLD:
        atm["nitrogen"] = {"in_atm": round(rng.uniform(0.001, 0.1), 4), "liquid": 0, "frozen_surface": 0, "frozen_deep": 0}
        atm["co2"] = {"in_atm": round(rng.uniform(0.001, 0.05), 4), "liquid": 0, "frozen_surface": round(rng.uniform(0.1, 2), 4), "frozen_deep": 0}
        atm["water"] = {"in_atm": 0, "liquid": 0, "frozen_surface": round(rng.uniform(0.5, 5), 4), "frozen_deep": round(rng.uniform(0, 3), 4)}
        return atm

    if planet_type == TYPE_EARTH_LIKE:
        atm["nitrogen"] = {"in_atm": round(rng.uniform(0.5, 1.5), 4), "liquid": 0, "frozen_surface": 0, "frozen_deep": 0}
        atm["oxygen"] = {"in_atm": round(rng.uniform(0.1, 0.5), 4), "liquid": 0, "frozen_surface": 0, "frozen_deep": 0}
        atm["co2"] = {"in_atm": round(rng.uniform(0.0001, 0.01), 4), "liquid": 0, "frozen_surface": 0, "frozen_deep": 0}
        water_liquid = round(rng.uniform(0.1, 0.8), 4)
        atm["water"] = {"in_atm": round(rng.uniform(0, 0.01), 4), "liquid": water_liquid, "frozen_surface": round(rng.uniform(0, 0.2), 4), "frozen_deep": 0}
        if rng.random() < 0.3:
            atm["methane"] = {"in_atm": round(rng.uniform(0.0001, 0.005), 4), "liquid": 0, "frozen_surface": 0, "frozen_deep": 0}
        return atm

    if planet_type == TYPE_VENUS_LIKE:
        atm["co2"] = {"in_atm": round(rng.uniform(1, 5), 4), "liquid": 0, "frozen_surface": 0, "frozen_deep": 0}
        atm["nitrogen"] = {"in_atm": round(rng.uniform(0.05, 0.5), 4), "liquid": 0, "frozen_surface": 0, "frozen_deep": 0}
        atm["water"] = {"in_atm": round(rng.uniform(0, 0.01), 4), "liquid": 0, "frozen_surface": 0, "frozen_deep": round(rng.uniform(0, 0.5), 4)}
        return atm

    # TYPE_ROCKY default
    atm["nitrogen"] = {"in_atm": round(rng.uniform(0.001, 0.5), 4), "liquid": 0, "frozen_surface": 0, "frozen_deep": 0}
    atm["co2"] = {"in_atm": round(rng.uniform(0.001, 0.1), 4), "liquid": 0, "frozen_surface": 0, "frozen_deep": 0}
    if eq_temp > 200 and eq_temp < 350:
        atm["oxygen"] = {"in_atm": round(rng.uniform(0.01, 0.2), 4), "liquid": 0, "frozen_surface": 0, "frozen_deep": 0}
    if eq_temp < 200:
        atm["water"] = {"in_atm": 0, "liquid": 0, "frozen_surface": round(rng.uniform(0.1, 2), 4), "frozen_deep": 0}
    return atm


def make_star_entry(hostname, x, y, z, temp, mass, planets, known=False):
    """Create a star entry for the universe JSON."""
    spectral_class = star_type_from_temp(temp)
    r, g, b = star_color_from_temp(temp)

    # Stars are large: mass maps roughly to radius
    # Using mass-luminosity relation proxy
    radius = max(0.5, mass ** 0.8) if mass > 0 else 1.0

    # Radiation intensity proportional to luminosity (L ~ M^3.5 for main sequence)
    luminosity = mass ** 3.5 if mass > 0 else 1.0
    radiation = min(10.0, max(0.5, luminosity))

    entry = {
        "name": hostname,
        "type": "star",
        "position": [round(x, 2), round(y, 2), round(z, 2)],
        "temperature": int(temp),
        "mass": round(mass, 3),
        "radius": round(radius, 3),
        "radiationIntensity": round(radiation, 3),
        "emissiveColor": [round(r, 3), round(g, 3), round(b, 3)],
        "isKnown": known,
        "planets": planets,
    }
    return entry


def make_planet_entry(pl_name, parent_name, semi_axis_au, radius_earth,
                      mass_earth, eq_temp, density, rng, orbital_offset=0):
    """Create a planet entry for the universe JSON."""
    if radius_earth <= 0:
        radius_earth = 1.0
    if mass_earth <= 0:
        mass_earth = radius_earth ** 2.5

    gravity = round(mass_earth / (radius_earth ** 2), 3) if radius_earth > 0 else 1.0

    planet_type = classify_planet(radius_earth, mass_earth, eq_temp, density)
    fluid, palette = classify_fluid_and_palette(eq_temp, planet_type)

    # Humidity: meaningful only for temperate worlds
    if 200 <= eq_temp <= 340:
        humidity = round(rng.uniform(0.2, 0.9), 3)
    else:
        humidity = round(rng.uniform(0, 0.1), 3)

    atmosphere = generate_atmosphere(planet_type, eq_temp, radius_earth, density, rng)

    # Determine biome preset
    if planet_type == TYPE_LAVA:
        biome = "MUSTAFAR"
    elif planet_type == TYPE_ICE_WORLD:
        biome = "MOON"
    elif planet_type == TYPE_EARTH_LIKE:
        biome = "OVERWORLD"
    elif planet_type == TYPE_VENUS_LIKE:
        biome = "VENUS"
    elif planet_type == TYPE_GAS_GIANT or planet_type == TYPE_ICE_GIANT:
        biome = None  # Can't visit gas giants
    else:
        biome = "MOON"

    can_visit = biome is not None and planet_type not in (TYPE_GAS_GIANT, TYPE_ICE_GIANT)

    entry = {
        "name": pl_name,
        "parentName": parent_name,
        "semiMajorAxisAU": round(semi_axis_au, 4),
        "radiusEarth": round(radius_earth, 3),
        "massEarth": round(mass_earth, 3),
        "gravity": gravity,
        "eqTemp": round(eq_temp, 1),
        "density": round(density, 2),
        "planetType": planet_type,
        "fluid": fluid,
        "palette": palette,
        "humidity": humidity,
        "atmosphere": atmosphere,
        "canVisit": can_visit,
        "biomePreset": biome,
        "seed": seed_from_name(pl_name),
        "orbitalOffsetDeg": round(orbital_offset, 1),
    }
    return entry


def fetch_nasa_data():
    """Fetch confirmed exoplanet systems from NASA Exoplanet Archive."""
    nasa_query = (
        "select hostname, ra, dec, sy_dist, st_teff, st_mass, pl_name, "
        "pl_orbsmax, pl_rade, pl_bmasse, pl_dens, pl_eqt from ps where default_flag=1"
    )
    nasa_url = (
        "https://exoplanetarchive.ipac.caltech.edu/TAP/sync?query="
        + urllib.parse.quote(nasa_query)
        + "&format=json"
    )

    print("Fetching confirmed systems from NASA Exoplanet Archive...")
    try:
        req = urllib.request.Request(nasa_url, headers={"User-Agent": "advRocketry/1.0"})
        with urllib.request.urlopen(req, timeout=60) as res:
            data = json.loads(res.read().decode())
        print(f"  Fetched {len(data)} rows from NASA.")
        return data
    except Exception as e:
        print(f"  NASA Query Warning: {e}")
        return []


def fetch_gaia_data():
    """Fetch nearby stars from Gaia DR3 via POST (Gaia TAP requires POST)."""
    gaia_query = (
        f"SELECT TOP {MAX_GAIA_STARS} source_id, ra, dec, parallax "
        f"FROM gaiadr3.gaia_source "
        f"WHERE parallax > 0.01"
    )
    gaia_url = "https://gea.esac.esa.int/tap-server/tap/sync"

    print("Fetching nearby stars from Gaia Archive...")
    try:
        data_bytes = ('request=doQuery&lang=ADQL&format=json&query=' + urllib.parse.quote(gaia_query)).encode()
        req = urllib.request.Request(
            gaia_url,
            data=data_bytes,
            headers={"User-Agent": "advRocketry/1.0", "Content-Type": "application/x-www-form-urlencoded"}
        )
        with urllib.request.urlopen(req, timeout=60) as res:
            raw = json.loads(res.read().decode())

        # Gaia TAP returns {metadata: [...], data: [...]}
        if isinstance(raw, dict) and "data" in raw:
            data_rows = raw["data"]
            fields = [col["name"] for col in raw.get("metadata", [])]
            rows = [dict(zip(fields, row)) for row in data_rows]
        elif isinstance(raw, list):
            rows = raw
        else:
            rows = []

        print(f"  Fetched {len(rows)} stars from Gaia.")
        return rows
    except Exception as e:
        print(f"  Gaia Query Warning: {e}")
    return []


def process_nasa_data(nasa_data):
    """Process NASA rows into star dict with planets."""
    stars = {}

    for row in nasa_data:
        hostname = row.get("hostname")
        if not hostname:
            continue

        if hostname not in stars:
            dist_pc = row.get("sy_dist") or 10.0
            ra = row.get("ra") or 0.0
            dec = row.get("dec") or 0.0

            # Convert to AU (mod coordinate system)
            dist_au = dist_pc * PARSECS_TO_AU

            # Spherical to Cartesian
            ra_rad = math.radians(ra)
            dec_rad = math.radians(dec)
            x = dist_au * math.cos(dec_rad) * math.cos(ra_rad)
            y = dist_au * math.cos(dec_rad) * math.sin(ra_rad)
            z = dist_au * math.sin(dec_rad)

            stars[hostname] = {
                "hostname": hostname,
                "x": x, "y": y, "z": z,
                "temp": int(row.get("st_teff") or 5778),
                "mass": float(row.get("st_mass") or 1.0),
                "planets": [],
            }

        pl_name = row.get("pl_name")
        if not pl_name:
            continue

        rad = row.get("pl_rade") or 1.0
        mass = row.get("pl_bmasse") or (rad ** 2.5)
        eq_temp = row.get("pl_eqt") or 250.0
        density = row.get("pl_dens") or 5.5
        semi_axis = row.get("pl_orbsmax") or 1.0

        rng = random.Random(pl_name)

        planet = make_planet_entry(
            pl_name=pl_name,
            parent_name=hostname,
            semi_axis_au=semi_axis,
            radius_earth=rad,
            mass_earth=mass,
            eq_temp=eq_temp,
            density=density,
            rng=rng,
            orbital_offset=round(rng.uniform(0, 360), 1),
        )
        stars[hostname]["planets"].append(planet)

    return stars


def simulate_planets_for_star(star_rng, hostname, star_temp, star_mass):
    """Generate procedural planets for a star without NASA data."""
    planets = []
    num_planets = star_rng.choices(
        [0, 1, 2, 3, 4, 5],
        weights=[15, 25, 25, 20, 10, 5]
    )[0]

    for p_idx in range(num_planets):
        p_name = f"{hostname} {chr(98 + p_idx)}"  # b, c, d, e, f, g
        semi_axis = round(0.1 + (p_idx * 0.5) + star_rng.uniform(0.05, 0.4), 3)

        # Temperature decreases with distance (Stefan-Boltzmann approximation)
        eq_temp = round(star_temp * math.sqrt(0.05 / max(semi_axis, 0.01)) * 0.4, 1)
        eq_temp = max(30, min(3000, eq_temp))

        # Planet radius: mix of small rocky and occasional gas giant
        if star_rng.random() < 0.15:
            rad = round(star_rng.uniform(3, 12), 2)  # Gas giant
        else:
            rad = round(star_rng.uniform(0.3, 2.5), 2)

        mass = round(rad ** 2.5 * star_rng.uniform(0.7, 1.3), 3)
        density = round(star_rng.uniform(2, 8), 2)

        planet = make_planet_entry(
            pl_name=p_name,
            parent_name=hostname,
            semi_axis_au=semi_axis,
            radius_earth=rad,
            mass_earth=mass,
            eq_temp=eq_temp,
            density=density,
            rng=star_rng,
            orbital_offset=round(star_rng.uniform(0, 360), 1),
        )
        planets.append(planet)

    return planets


def process_gaia_data(gaia_rows, nasa_hostnames):
    """Process Gaia data into stars, skipping any already in NASA data."""
    stars = {}

    for idx, row in enumerate(gaia_rows):
        source_id = str(row.get("source_id", f"GaiaUnconfirmed_{idx}"))
        hostname = f"Gaia-{source_id[-8:]}"
        if hostname in nasa_hostnames:
            continue

        # Parallax in mas -> distance in parsecs = 1000 / parallax
        parallax = float(row.get("parallax") or 10.0)
        if parallax <= 0:
            continue
        dist_pc = 1000.0 / parallax
        ra = float(row.get("ra") or 0.0)
        dec = float(row.get("dec") or 0.0)

        # Convert to AU
        dist_au = dist_pc * PARSECS_TO_AU
        ra_rad = math.radians(ra)
        dec_rad = math.radians(dec)
        x = dist_au * math.cos(dec_rad) * math.cos(ra_rad)
        y = dist_au * math.cos(dec_rad) * math.sin(ra_rad)
        z = dist_au * math.sin(dec_rad)

        # No direct temperature/mass from this Gaia query, simulate procedurally
        star_rng = random.Random(source_id)
        star_temp = int(star_rng.gauss(5500, 1200))
        star_temp = max(2500, min(15000, star_temp))
        star_mass = round((star_temp / 5500.0) ** 0.5, 3)
        star_mass = max(0.1, min(50.0, star_mass))

        planets = simulate_planets_for_star(star_rng, hostname, star_temp, star_mass)

        stars[hostname] = {
            "hostname": hostname,
            "x": x, "y": y, "z": z,
            "temp": star_temp,
            "mass": star_mass,
            "planets": planets,
        }

    return stars


def add_sol_system(stars_dict):
    """Add our Solar System as the starting point."""
    # Sol at origin
    sol_planets = [
        make_planet_entry("Mercury", "Sol", 0.387, 0.383, 0.055, 440, 5.43,
                          random.Random("Mercury"), 29),
        make_planet_entry("Venus", "Sol", 0.723, 0.949, 0.815, 737, 5.24,
                          random.Random("Venus"), 55),
        # Earth is handled by the mod's existing DefaultGalaxy
        make_planet_entry("Mars", "Sol", 1.524, 0.532, 0.107, 210, 3.93,
                          random.Random("Mars"), 120),
        make_planet_entry("Jupiter", "Sol", 5.203, 11.21, 317.8, 165, 1.33,
                          random.Random("Jupiter"), 200),
        make_planet_entry("Saturn", "Sol", 9.537, 9.45, 95.16, 134, 0.687,
                          random.Random("Saturn"), 310),
        make_planet_entry("Uranus", "Sol", 19.19, 4.01, 14.54, 59, 1.27,
                          random.Random("Uranus"), 90),
        make_planet_entry("Neptune", "Sol", 30.07, 3.88, 17.15, 51, 1.64,
                          random.Random("Neptune"), 270),
    ]

    # Mark Jupiter and Saturn as canGasMine
    for p in sol_planets:
        if p["name"] in ("Jupiter", "Saturn"):
            p["canGasMine"] = True

    stars_dict["Sol"] = {
        "hostname": "Sol",
        "x": 0, "y": 0, "z": 0,
        "temp": 5778,
        "mass": 1.0,
        "planets": sol_planets,
        "isSol": True,
    }


def build_universe():
    """Main pipeline: fetch data, process, output JSON."""
    nasa_data = fetch_nasa_data()
    gaia_rows = fetch_gaia_data()

    # Process NASA confirmed systems
    nasa_stars = process_nasa_data(nasa_data)
    print(f"Processed {len(nasa_stars)} NASA star systems with planets.")

    # Process Gaia nearby stars (simulate planets)
    gaia_stars = process_gaia_data(gaia_rows, set(nasa_stars.keys()))
    print(f"Processed {len(gaia_stars)} Gaia stars with simulated planets.")

    # Merge: NASA takes priority
    all_stars = {**gaia_stars, **nasa_stars}

    # Add Sol system
    add_sol_system(all_stars)

    # Build final output
    universe = []
    total_planets = 0
    for hostname, star in sorted(all_stars.items()):
        entry = make_star_entry(
            hostname=star["hostname"],
            x=star["x"],
            y=star["y"],
            z=star["z"],
            temp=star["temp"],
            mass=star["mass"],
            planets=star["planets"],
            known=star.get("isSol", False),
        )
        if star.get("isSol"):
            entry["isSol"] = True
        universe.append(entry)
        total_planets += len(star["planets"])

    output = {
        "version": 1,
        "description": "NASA Exoplanet Archive + Gaia DR3 stellar data",
        "generated": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "totalStars": len(universe),
        "totalPlanets": total_planets,
        "parsecsToAU": PARSECS_TO_AU,
        "bodies": universe,
    }

    return output


def main():
    universe = build_universe()

    # Anchor to the project root (one level up from the 'tools' directory)
    script_dir = Path(__file__).resolve().parent
    project_root = script_dir.parent
    output_path = project_root / "src" / "main" / "resources" / "data" / "adv_rocketry" / "universe_data.json"

    print(f"\nWriting {universe['totalStars']} stars, {universe['totalPlanets']} planets to {output_path}...")

    # Ensure all parent directories exist before writing
    output_path.parent.mkdir(parents=True, exist_ok=True)

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(universe, f, separators=(",", ":"))

    file_size_mb = len(json.dumps(universe, separators=(",", ":")).encode()) / (1024 * 1024)
    print(f"Done! File size: {file_size_mb:.2f} MB")
    print(f"Stars: {universe['totalStars']}, Planets: {universe['totalPlanets']}")

if __name__ == "__main__":
    main()
