package com.example.converter.service.unit;

import com.example.converter.dto.conversion.UnitConversionRequest;
import com.example.converter.dto.conversion.UnitConversionResponse;
import com.example.converter.entity.UnitConversionLog;
import com.example.converter.repository.UnitConversionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Boot Service for unit conversion functionality.
 * Method: convertUnit(double value, String fromUnit, String toUnit).
 * Logic: Perform unit conversion based on defined formulas.
 * Uses UnitConversionRepository to log conversion history in unit_conversion_log table.
 * Handles invalid units or not found cases.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnitConversionService {

    private final UnitConversionRepository unitConversionRepository;

    // Comprehensive conversion factors for different units
    private static final Map<String, Map<String, Double>> CONVERSION_FACTORS = new HashMap<>();

    static {
        // Length conversions (to meters)
        Map<String, Double> lengthConversions = new HashMap<>();
        lengthConversions.put("meter", 1.0);
        lengthConversions.put("m", 1.0);
        lengthConversions.put("centimeter", 0.01);
        lengthConversions.put("cm", 0.01);
        lengthConversions.put("millimeter", 0.001);
        lengthConversions.put("mm", 0.001);
        lengthConversions.put("kilometer", 1000.0);
        lengthConversions.put("km", 1000.0);
        lengthConversions.put("inch", 0.0254);
        lengthConversions.put("in", 0.0254);
        lengthConversions.put("foot", 0.3048);
        lengthConversions.put("ft", 0.3048);
        lengthConversions.put("yard", 0.9144);
        lengthConversions.put("yd", 0.9144);
        lengthConversions.put("mile", 1609.344);
        lengthConversions.put("mi", 1609.344);
        lengthConversions.put("nautical_mile", 1852.0);
        lengthConversions.put("nmi", 1852.0);
        CONVERSION_FACTORS.put("length", lengthConversions);

        // Weight conversions (to kilograms)
        Map<String, Double> weightConversions = new HashMap<>();
        weightConversions.put("kilogram", 1.0);
        weightConversions.put("kg", 1.0);
        weightConversions.put("gram", 0.001);
        weightConversions.put("g", 0.001);
        weightConversions.put("milligram", 0.000001);
        weightConversions.put("mg", 0.000001);
        weightConversions.put("pound", 0.453592);
        weightConversions.put("lb", 0.453592);
        weightConversions.put("ounce", 0.0283495);
        weightConversions.put("oz", 0.0283495);
        weightConversions.put("ton", 1000.0);
        weightConversions.put("t", 1000.0);
        weightConversions.put("stone", 6.35029);
        weightConversions.put("st", 6.35029);
        CONVERSION_FACTORS.put("weight", weightConversions);

        // Volume conversions (to liters)
        Map<String, Double> volumeConversions = new HashMap<>();
        volumeConversions.put("liter", 1.0);
        volumeConversions.put("l", 1.0);
        volumeConversions.put("milliliter", 0.001);
        volumeConversions.put("ml", 0.001);
        volumeConversions.put("cubic_meter", 1000.0);
        volumeConversions.put("m3", 1000.0);
        volumeConversions.put("cubic_centimeter", 0.001);
        volumeConversions.put("cm3", 0.001);
        volumeConversions.put("gallon", 3.78541);
        volumeConversions.put("gal", 3.78541);
        volumeConversions.put("quart", 0.946353);
        volumeConversions.put("qt", 0.946353);
        volumeConversions.put("pint", 0.473176);
        volumeConversions.put("pt", 0.473176);
        volumeConversions.put("cup", 0.236588);
        volumeConversions.put("fluid_ounce", 0.0295735);
        volumeConversions.put("fl_oz", 0.0295735);
        CONVERSION_FACTORS.put("volume", volumeConversions);

        // Area conversions (to square meters)
        Map<String, Double> areaConversions = new HashMap<>();
        areaConversions.put("square_meter", 1.0);
        areaConversions.put("m2", 1.0);
        areaConversions.put("square_centimeter", 0.0001);
        areaConversions.put("cm2", 0.0001);
        areaConversions.put("square_millimeter", 0.000001);
        areaConversions.put("mm2", 0.000001);
        areaConversions.put("square_kilometer", 1000000.0);
        areaConversions.put("km2", 1000000.0);
        areaConversions.put("square_inch", 0.00064516);
        areaConversions.put("in2", 0.00064516);
        areaConversions.put("square_foot", 0.092903);
        areaConversions.put("ft2", 0.092903);
        areaConversions.put("square_yard", 0.836127);
        areaConversions.put("yd2", 0.836127);
        areaConversions.put("acre", 4046.86);
        areaConversions.put("hectare", 10000.0);
        areaConversions.put("ha", 10000.0);
        CONVERSION_FACTORS.put("area", areaConversions);

        // Speed conversions (to m/s)
        Map<String, Double> speedConversions = new HashMap<>();
        speedConversions.put("meter_per_second", 1.0);
        speedConversions.put("mps", 1.0);
        speedConversions.put("kilometer_per_hour", 0.277778);
        speedConversions.put("kmh", 0.277778);
        speedConversions.put("mile_per_hour", 0.44704);
        speedConversions.put("mph", 0.44704);
        speedConversions.put("foot_per_second", 0.3048);
        speedConversions.put("fps", 0.3048);
        speedConversions.put("knot", 0.514444);
        speedConversions.put("kn", 0.514444);
        CONVERSION_FACTORS.put("speed", speedConversions);

        // Energy conversions (to joules)
        Map<String, Double> energyConversions = new HashMap<>();
        energyConversions.put("joule", 1.0);
        energyConversions.put("j", 1.0);
        energyConversions.put("kilojoule", 1000.0);
        energyConversions.put("kj", 1000.0);
        energyConversions.put("calorie", 4.184);
        energyConversions.put("cal", 4.184);
        energyConversions.put("kilocalorie", 4184.0);
        energyConversions.put("kcal", 4184.0);
        energyConversions.put("watt_hour", 3600.0);
        energyConversions.put("wh", 3600.0);
        energyConversions.put("kilowatt_hour", 3600000.0);
        energyConversions.put("kwh", 3600000.0);
        energyConversions.put("british_thermal_unit", 1055.06);
        energyConversions.put("btu", 1055.06);
        CONVERSION_FACTORS.put("energy", energyConversions);

        // Power conversions (to watts)
        Map<String, Double> powerConversions = new HashMap<>();
        powerConversions.put("watt", 1.0);
        powerConversions.put("w", 1.0);
        powerConversions.put("kilowatt", 1000.0);
        powerConversions.put("kw", 1000.0);
        powerConversions.put("megawatt", 1000000.0);
        powerConversions.put("mw", 1000000.0);
        powerConversions.put("horsepower", 745.7);
        powerConversions.put("hp", 745.7);
        powerConversions.put("british_thermal_unit_per_hour", 0.293071);
        powerConversions.put("btu_per_hour", 0.293071);
        CONVERSION_FACTORS.put("power", powerConversions);

        // Pressure conversions (to pascals)
        Map<String, Double> pressureConversions = new HashMap<>();
        pressureConversions.put("pascal", 1.0);
        pressureConversions.put("pa", 1.0);
        pressureConversions.put("kilopascal", 1000.0);
        pressureConversions.put("kpa", 1000.0);
        pressureConversions.put("bar", 100000.0);
        pressureConversions.put("atmosphere", 101325.0);
        pressureConversions.put("atm", 101325.0);
        pressureConversions.put("psi", 6894.76);
        pressureConversions.put("mmhg", 133.322);
        pressureConversions.put("torr", 133.322);
        CONVERSION_FACTORS.put("pressure", pressureConversions);

        // Time conversions (to seconds)
        Map<String, Double> timeConversions = new HashMap<>();
        timeConversions.put("second", 1.0);
        timeConversions.put("s", 1.0);
        timeConversions.put("minute", 60.0);
        timeConversions.put("min", 60.0);
        timeConversions.put("hour", 3600.0);
        timeConversions.put("h", 3600.0);
        timeConversions.put("day", 86400.0);
        timeConversions.put("d", 86400.0);
        timeConversions.put("week", 604800.0);
        timeConversions.put("month", 2592000.0); // 30 days
        timeConversions.put("year", 31536000.0); // 365 days
        CONVERSION_FACTORS.put("time", timeConversions);

        // Temperature conversions (handled separately)
        Map<String, Double> temperatureConversions = new HashMap<>();
        temperatureConversions.put("celsius", 1.0);
        temperatureConversions.put("c", 1.0);
        temperatureConversions.put("fahrenheit", 1.0);
        temperatureConversions.put("f", 1.0);
        temperatureConversions.put("kelvin", 1.0);
        temperatureConversions.put("k", 1.0);
        CONVERSION_FACTORS.put("temperature", temperatureConversions);
    }

    /**
     * Convert unit from one unit to another
     * @param request the conversion request
     * @param userId the user ID for logging
     * @return UnitConversionResponse with converted value
     */
    public UnitConversionResponse convertUnit(UnitConversionRequest request, Long userId) {
        log.info("Converting {} {} to {}", request.getValue(), request.getFromUnit(), request.getToUnit());

        try {
            double convertedValue = convertUnitValue(request.getValue(), request.getFromUnit(), request.getToUnit());
            
            // Log the conversion only if we have an authenticated user
            if (userId != null) {
                try {
                    UnitConversionLog conversionLog = new UnitConversionLog();
                    conversionLog.setFromUnit(request.getFromUnit());
                    conversionLog.setToUnit(request.getToUnit());
                    conversionLog.setInputValue(BigDecimal.valueOf(request.getValue()));
                    conversionLog.setOutputValue(BigDecimal.valueOf(convertedValue));
                    conversionLog.setConvertedAt(LocalDateTime.now());
                    conversionLog.setUserId(userId);
                    
                    unitConversionRepository.save(conversionLog);
                    log.info("Saved unit conversion log for user: {}", userId);
                } catch (Exception e) {
                    log.warn("Failed to save unit conversion log: {}", e.getMessage());
                }
            } else {
                log.debug("Skipping log save - no authenticated user");
            }
            
            log.info("Successfully converted {} {} to {} {}", 
                    request.getValue(), request.getFromUnit(), convertedValue, request.getToUnit());
            
            return new UnitConversionResponse(convertedValue, request.getToUnit());
            
        } catch (IllegalArgumentException e) {
            log.error("Unit conversion error: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Convert unit value using conversion factors
     * @param value the value to convert
     * @param fromUnit the source unit
     * @param toUnit the target unit
     * @return converted value
     */
    private double convertUnitValue(double value, String fromUnit, String toUnit) {
        // Handle temperature conversions (special case)
        if (isTemperatureUnit(fromUnit) && isTemperatureUnit(toUnit)) {
            return convertTemperature(value, fromUnit, toUnit);
        }

        // Handle other conversions using conversion factors
        String category = getUnitCategory(fromUnit);
        if (category == null) {
            throw new IllegalArgumentException("Unknown unit: " + fromUnit);
        }

        Map<String, Double> conversions = CONVERSION_FACTORS.get(category);
        if (conversions == null || !conversions.containsKey(fromUnit) || !conversions.containsKey(toUnit)) {
            throw new IllegalArgumentException("Unsupported conversion from " + fromUnit + " to " + toUnit);
        }

        // Convert to base unit, then to target unit
        double baseValue = value * conversions.get(fromUnit);
        return baseValue / conversions.get(toUnit);
    }

    /**
     * Convert temperature between different scales
     * @param value the temperature value
     * @param fromUnit the source temperature unit
     * @param toUnit the target temperature unit
     * @return converted temperature
     */
    private double convertTemperature(double value, String fromUnit, String toUnit) {
        String fromLower = fromUnit.toLowerCase();
        String toLower = toUnit.toLowerCase();
        
        // Convert to Celsius first
        double celsius;
        switch (fromLower) {
            case "celsius":
            case "c":
                celsius = value;
                break;
            case "fahrenheit":
            case "f":
                celsius = (value - 32) * 5.0 / 9.0;
                break;
            case "kelvin":
            case "k":
                celsius = value - 273.15;
                break;
            default:
                throw new IllegalArgumentException("Unknown temperature unit: " + fromUnit);
        }

        // Convert from Celsius to target unit
        switch (toLower) {
            case "celsius":
            case "c":
                return celsius;
            case "fahrenheit":
            case "f":
                return celsius * 9.0 / 5.0 + 32;
            case "kelvin":
            case "k":
                return celsius + 273.15;
            default:
                throw new IllegalArgumentException("Unknown temperature unit: " + toUnit);
        }
    }

    /**
     * Check if unit is a temperature unit
     * @param unit the unit to check
     * @return true if temperature unit
     */
    private boolean isTemperatureUnit(String unit) {
        String lowerUnit = unit.toLowerCase();
        return "celsius".equals(lowerUnit) || "c".equals(lowerUnit) ||
               "fahrenheit".equals(lowerUnit) || "f".equals(lowerUnit) ||
               "kelvin".equals(lowerUnit) || "k".equals(lowerUnit);
    }

    /**
     * Get the category of a unit
     * @param unit the unit to check
     * @return the category of the unit
     */
    private String getUnitCategory(String unit) {
        String lowerUnit = unit.toLowerCase();
        for (Map.Entry<String, Map<String, Double>> entry : CONVERSION_FACTORS.entrySet()) {
            if (entry.getValue().containsKey(lowerUnit)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Get all available units by category
     * @return Map of categories and their units
     */
    public Map<String, String[]> getAvailableUnits() {
        Map<String, String[]> availableUnits = new HashMap<>();
        
        // Length units (canonical only)
        availableUnits.put("length", new String[]{
            "meter", "centimeter", "millimeter", "kilometer",
            "inch", "foot", "yard", "mile", "nautical_mile"
        });
        
        // Weight units (canonical only)
        availableUnits.put("weight", new String[]{
            "kilogram", "gram", "milligram", "pound",
            "ounce", "ton", "stone"
        });
        
        // Volume units (canonical only)
        availableUnits.put("volume", new String[]{
            "liter", "milliliter", "cubic_meter", "cubic_centimeter",
            "gallon", "quart", "pint", "cup", "fluid_ounce"
        });
        
        // Area units (canonical only)
        availableUnits.put("area", new String[]{
            "square_meter", "square_centimeter", "square_millimeter",
            "square_kilometer", "square_inch", "square_foot",
            "square_yard", "acre", "hectare"
        });
        
        // Speed units (canonical only)
        availableUnits.put("speed", new String[]{
            "meter_per_second", "kilometer_per_hour", "mile_per_hour",
            "foot_per_second", "knot"
        });
        
        // Energy units (canonical only)
        availableUnits.put("energy", new String[]{
            "joule", "kilojoule", "calorie", "kilocalorie",
            "watt_hour", "kilowatt_hour", "british_thermal_unit"
        });
        
        // Power units (canonical only)
        availableUnits.put("power", new String[]{
            "watt", "kilowatt", "megawatt", "horsepower",
            "british_thermal_unit_per_hour"
        });
        
        // Pressure units (canonical only; keep common canonical identifiers)
        availableUnits.put("pressure", new String[]{
            "pascal", "kilopascal", "bar", "atmosphere",
            "psi", "mmhg", "torr"
        });
        
        // Time units (canonical only)
        availableUnits.put("time", new String[]{
            "second", "minute", "hour", "day",
            "week", "month", "year"
        });
        
        // Temperature units (canonical only)
        availableUnits.put("temperature", new String[]{
            "celsius", "fahrenheit", "kelvin"
        });
        
        return availableUnits;
    }
}
