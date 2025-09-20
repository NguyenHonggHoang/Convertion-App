import React, { useState, useEffect } from 'react';
import {
  Box,
  Paper,
  TextField,
  Button,
  Typography,
  Grid,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Alert,
  Card,
  CardContent,
  IconButton,
  Tooltip,
  Chip,
  Divider,
  CircularProgress,
  Fade,
  Stack
} from '@mui/material';
import {
  Mic,
  MicOff,
  SwapHoriz,
  Speed,
  Straighten,
  Scale,
  Thermostat,
  SquareFoot,
  LocalGasStation,
  Bolt,
  Compress,
  Schedule,
  Science
} from '@mui/icons-material';
import api from '../services/api';
import { NLP_BASE_URL } from '../utils/config';

const UnitConversionComponent = () => {
  
  const [value, setValue] = useState('');
  const [fromUnit, setFromUnit] = useState('');
  const [toUnit, setToUnit] = useState('');
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [isListening, setIsListening] = useState(false);
  const [recognition, setRecognition] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [availableUnits, setAvailableUnits] = useState({});
  const [selectedCategory, setSelectedCategory] = useState('');


  // Category icons mapping
  const categoryIcons = {
    length: <Straighten />,
    weight: <Scale />,
    temperature: <Thermostat />,
    volume: <LocalGasStation />,
    area: <SquareFoot />,
    speed: <Speed />,
    energy: <Bolt />,
    power: <Bolt />,
    pressure: <Compress />,
    time: <Schedule />,
  };

  // Category display names
  const categoryDisplayNames = {
    length: 'Length & Distance',
    weight: 'Weight & Mass',
    temperature: 'Temperature',
    volume: 'Volume & Capacity',
    area: 'Area',
    speed: 'Speed & Velocity',
    energy: 'Energy',
    power: 'Power',
    pressure: 'Pressure',
    time: 'Time',
  };

  // Popular conversion suggestions by category
  const popularConversions = {
    length: [
      { from: 'meter', to: 'foot', label: 'm → ft' },
      { from: 'centimeter', to: 'inch', label: 'cm → in' },
      { from: 'kilometer', to: 'mile', label: 'km → mi' },
      { from: 'millimeter', to: 'inch', label: 'mm → in' }
    ],
    weight: [
      { from: 'kilogram', to: 'pound', label: 'kg → lb' },
      { from: 'gram', to: 'ounce', label: 'g → oz' },
      { from: 'ton', to: 'pound', label: 't → lb' },
      { from: 'stone', to: 'kilogram', label: 'st → kg' }
    ],
    temperature: [
      { from: 'celsius', to: 'fahrenheit', label: '°C → °F' },
      { from: 'fahrenheit', to: 'celsius', label: '°F → °C' },
      { from: 'kelvin', to: 'celsius', label: 'K → °C' },
      { from: 'celsius', to: 'kelvin', label: '°C → K' }
    ],
    volume: [
      { from: 'liter', to: 'gallon', label: 'L → gal' },
      { from: 'milliliter', to: 'fluid_ounce', label: 'mL → fl oz' },
      { from: 'cubic_meter', to: 'liter', label: 'm³ → L' },
      { from: 'cup', to: 'milliliter', label: 'cup → mL' }
    ],
    area: [
      { from: 'square_meter', to: 'square_foot', label: 'm² → ft²' },
      { from: 'square_kilometer', to: 'acre', label: 'km² → acre' },
      { from: 'hectare', to: 'acre', label: 'ha → acre' },
      { from: 'square_inch', to: 'square_centimeter', label: 'in² → cm²' }
    ],
    speed: [
      { from: 'kilometer_per_hour', to: 'mile_per_hour', label: 'km/h → mph' },
      { from: 'meter_per_second', to: 'kilometer_per_hour', label: 'm/s → km/h' },
      { from: 'knot', to: 'kilometer_per_hour', label: 'kn → km/h' },
      { from: 'mile_per_hour', to: 'meter_per_second', label: 'mph → m/s' }
    ]
  };

  useEffect(() => {
    
    // Load available units from backend - only once
    loadAvailableUnits();
    
    // Initialize Web Speech API
    if ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window) {
      const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
      const recognitionInstance = new SpeechRecognition();
      recognitionInstance.continuous = false;
      recognitionInstance.interimResults = false;
      recognitionInstance.lang = 'en-US';

      recognitionInstance.onresult = (event) => {
        const transcript = event.results[0][0].transcript.toLowerCase();
        parseVoiceCommand(transcript);
        setIsListening(false);
      };

      recognitionInstance.onerror = (event) => {
  // Speech recognition error handled silently
        setIsListening(false);
      };

      setRecognition(recognitionInstance);
    }
  }, []); // Empty dependency array to run only once

  const loadAvailableUnits = async () => {
    try {
      const response = await api.get('/api/convert/unit/units');
      
      // Super comprehensive deduplication
      const cleanedUnits = {};
      Object.entries(response.data).forEach(([category, units]) => {
        
        // Step 1: Filter out null/undefined/empty values and ensure strings
        const validUnits = units.filter(unit => 
          unit && 
          typeof unit === 'string' && 
          unit.trim() && 
          unit.trim().length > 0
        );
        
        // Step 2: Normalize to lowercase and trim
        const normalizedUnits = validUnits.map(unit => unit.trim().toLowerCase());
        
        // Step 3: Remove duplicates using Set
        const uniqueUnits = [...new Set(normalizedUnits)];
        
        // Step 4: Sort alphabetically for consistent ordering
        cleanedUnits[category] = uniqueUnits.sort();
        
      });
      
      setAvailableUnits(cleanedUnits);
      
    } catch (error) {
      const fallbackUnits = {
        length: ['centimeter', 'foot', 'inch', 'kilometer', 'meter', 'mile', 'millimeter', 'yard'],
        weight: ['gram', 'kilogram', 'ounce', 'pound', 'stone', 'ton'],
        temperature: ['celsius', 'fahrenheit', 'kelvin'],
        volume: ['cup', 'cubic_meter', 'fluid_ounce', 'gallon', 'liter', 'milliliter'],
        area: ['acre', 'hectare', 'square_foot', 'square_inch', 'square_kilometer', 'square_meter'],
        speed: ['kilometer_per_hour', 'knot', 'meter_per_second', 'mile_per_hour'],
        energy: ['btu', 'calorie', 'joule', 'kilowatt_hour'],
        power: ['horsepower', 'kilowatt', 'watt'],
        pressure: ['atmosphere', 'bar', 'pascal', 'psi'],
        time: ['day', 'hour', 'minute', 'month', 'second', 'week', 'year']
      };
      setAvailableUnits(fallbackUnits);
    }
  };

  const parseVoiceCommand = (transcript) => {
    const words = transcript.split(' ');
    
    // Look for numbers
    const numberMatch = transcript.match(/\d+(\.\d+)?/);
    if (numberMatch) {
      setValue(numberMatch[0]);
    }

    // Look for units from available units
    const allUnits = Object.values(availableUnits).flat();
    for (const unit of allUnits) {
      if (transcript.includes(unit)) {
        if (!fromUnit) {
          setFromUnit(unit);
        } else if (!toUnit) {
          setToUnit(unit);
        }
      }
    }

    // Look for "to" keyword
    const toIndex = words.indexOf('to');
    if (toIndex > 0 && toIndex < words.length - 1) {
      const fromWord = words[toIndex - 1];
      const toWord = words[toIndex + 1];
      
      if (allUnits.includes(fromWord)) {
        setFromUnit(fromWord);
      }
      if (allUnits.includes(toWord)) {
        setToUnit(toWord);
      }
    }
  };

  const handleConvert = async () => {
    if (!value || !fromUnit || !toUnit) {
      setError('Please fill in all fields');
      return;
    }

    try {
      setError('');
      setIsLoading(true);
      const response = await api.post('/api/convert/unit', {
        value: parseFloat(value),
        fromUnit: fromUnit,
        toUnit: toUnit
      });

      setResult(response.data);
    } catch (err) {
      setError(err.response?.data?.message || 'Conversion failed');
    } finally {
      setIsLoading(false);
    }
  };

  const handleSwapUnits = () => {
    setFromUnit(toUnit);
    setToUnit(fromUnit);
  };

  const handleVoiceRecognition = () => {
    if (recognition) {
      if (isListening) {
        recognition.stop();
        setIsListening(false);
      } else {
        recognition.start();
        setIsListening(true);
      }
    }
  };

  const handleCategorySelect = (category) => {
    setSelectedCategory(category);
    setFromUnit('');
    setToUnit('');
    setResult(null);
    setError('');
  };

  const handleSuggestionClick = (suggestion) => {
    const currentUnits = availableUnits[selectedCategory] || [];
    
    let fromUnit = currentUnits.find(unit => 
      unit.toLowerCase() === suggestion.from.toLowerCase()
    );
    
    let toUnit = currentUnits.find(unit => 
      unit.toLowerCase() === suggestion.to.toLowerCase()
    );

    if (!fromUnit) {
      fromUnit = currentUnits.find(unit => 
        unit.toLowerCase().includes(suggestion.from.toLowerCase()) ||
        suggestion.from.toLowerCase().includes(unit.toLowerCase())
      );
    }

    if (!toUnit) {
      toUnit = currentUnits.find(unit => 
        unit.toLowerCase().includes(suggestion.to.toLowerCase()) ||
        suggestion.to.toLowerCase().includes(unit.toLowerCase())
      );
    }


    if (fromUnit) {
      setFromUnit(fromUnit);
    } else {
    }
    
    if (toUnit) {
      setToUnit(toUnit);
    } else {
    }

    if (fromUnit && toUnit && value) {
      handleConvert();
    }
  };

  const formatUnitDisplay = (unit) => {
    // Convert unit codes to user-friendly display names with proper symbols
    const unitDisplayNames = {
      // Length units
      'meter': 'm', 'centimeter': 'cm', 'millimeter': 'mm', 'kilometer': 'km',
      'inch': 'in', 'foot': 'ft', 'yard': 'yd', 'mile': 'mi', 'nautical_mile': 'nmi',
      
      // Weight units  
      'kilogram': 'kg', 'gram': 'g', 'milligram': 'mg', 'pound': 'lb',
      'ounce': 'oz', 'ton': 't', 'stone': 'st',
      
      // Temperature units
      'celsius': '°C', 'fahrenheit': '°F', 'kelvin': 'K',
      
      // Volume units
      'liter': 'L', 'milliliter': 'mL', 'cubic_meter': 'm³', 'cubic_centimeter': 'cm³',
      'gallon': 'gal', 'quart': 'qt', 'pint': 'pt', 'cup': 'cup', 'fluid_ounce': 'fl oz',
      
      // Area units - using proper symbols
      'square_meter': 'm²', 'square_centimeter': 'cm²', 'square_millimeter': 'mm²',
      'square_kilometer': 'km²', 'square_inch': 'in²', 'square_foot': 'ft²',
      'square_yard': 'yd²', 'acre': 'acre', 'hectare': 'ha',
      
      // Speed units - using proper symbols
      'meter_per_second': 'm/s', 'kilometer_per_hour': 'km/h', 'mile_per_hour': 'mph',
      'foot_per_second': 'ft/s', 'knot': 'kn',
      
      // Energy units
      'joule': 'J', 'kilojoule': 'kJ', 'calorie': 'cal', 'kilocalorie': 'kcal',
      'watt_hour': 'Wh', 'kilowatt_hour': 'kWh', 'british_thermal_unit': 'BTU',
      
      // Power units
      'watt': 'W', 'kilowatt': 'kW', 'megawatt': 'MW', 'horsepower': 'hp',
      'british_thermal_unit_per_hour': 'BTU/h',
      
      // Pressure units
      'pascal': 'Pa', 'kilopascal': 'kPa', 'bar': 'bar', 'atmosphere': 'atm',
      'psi': 'PSI', 'mmhg': 'mmHg', 'torr': 'Torr',
      
      // Time units
      'second': 's', 'minute': 'min', 'hour': 'h', 'day': 'd',
      'week': 'wk', 'month': 'mo', 'year': 'yr'
    };
    
    return unitDisplayNames[unit] || unit.charAt(0).toUpperCase() + unit.slice(1);
  };

  // Human-friendly unit names for canonical codes (used alongside symbols)
  const formatUnitName = (unit) => {
    const overrides = {
      // Length
      nautical_mile: 'Nautical mile',
      // Area
      square_meter: 'Square meter',
      square_centimeter: 'Square centimeter',
      square_millimeter: 'Square millimeter',
      square_kilometer: 'Square kilometer',
      square_inch: 'Square inch',
      square_foot: 'Square foot',
      square_yard: 'Square yard',
      // Volume
      cubic_meter: 'Cubic meter',
      cubic_centimeter: 'Cubic centimeter',
      fluid_ounce: 'Fluid ounce',
      // Speed
      meter_per_second: 'Meter per second',
      kilometer_per_hour: 'Kilometer per hour',
      mile_per_hour: 'Mile per hour',
      foot_per_second: 'Foot per second',
      // Energy / Power
      watt_hour: 'Watt hour',
      kilowatt_hour: 'Kilowatt hour',
      british_thermal_unit: 'British thermal unit',
      british_thermal_unit_per_hour: 'BTU per hour',
      // Pressure
      atmosphere: 'Atmosphere',
      psi: 'Pounds per square inch',
      mmhg: 'Millimeter of mercury',
      torr: 'Torr',
      // Time
      minute: 'Minute',
      hour: 'Hour',
      day: 'Day',
      week: 'Week',
      month: 'Month',
      year: 'Year'
    };
    if (overrides[unit]) return overrides[unit];
    // Default: replace underscores with spaces and Title Case
    const words = unit.split('_').map(w => w.length ? w[0].toUpperCase() + w.slice(1) : w);
    return words.join(' ');
  };

  const getUnitsForCategory = (category) => {
  const units = availableUnits[category] || [];
    
    // Additional check for duplicates at render time
    const uniqueUnits = [...new Set(units)];
    if (units.length !== uniqueUnits.length) {
      return uniqueUnits;
    }
    
    return units;
  };

  return (
    <Box sx={{ maxWidth: 1200, mx: 'auto', p: 2 }}>
      <Typography variant="h4" gutterBottom sx={{ textAlign: 'center', fontWeight: 600, mb: 4 }}>
        Smart Unit Converter
      </Typography>

      {/* Category Selection */}
      <Paper elevation={3} sx={{ p: 3, mb: 3, background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' }}>
        <Typography variant="h6" sx={{ mb: 2, color: 'white' }}>
          Select Category
        </Typography>
        <Grid container spacing={2}>
          {Object.entries(availableUnits).map(([category, units]) => (
            <Grid item xs={6} sm={4} md={2.4} key={category}>
              <Card 
                sx={{ 
                  cursor: 'pointer',
                  transition: 'all 0.3s ease',
                  transform: selectedCategory === category ? 'scale(1.05)' : 'scale(1)',
                  bgcolor: selectedCategory === category ? 'primary.main' : 'background.paper',
                  color: selectedCategory === category ? 'white' : 'text.primary',
                  '&:hover': { 
                    transform: 'scale(1.03)',
                    boxShadow: 6
                  }
                }}
                onClick={() => handleCategorySelect(category)}
              >
                <CardContent sx={{ textAlign: 'center', p: 2 }}>
                  <Box sx={{ mb: 1 }}>
                    {categoryIcons[category] || <Science />}
                  </Box>
                  <Typography variant="body2" sx={{ fontWeight: 500 }}>
                    {categoryDisplayNames[category] || category}
                  </Typography>
                  <Typography variant="caption" sx={{ opacity: 0.7 }}>
                    {units.length} units
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      </Paper>

      {/* Conversion Interface */}
      <Fade in={selectedCategory !== ''}>
        <Paper elevation={3} sx={{ p: 4, mb: 3 }}>
          <Typography variant="h6" gutterBottom sx={{ color: 'primary.main', fontWeight: 600 }}>
            {categoryDisplayNames[selectedCategory]} Conversion
          </Typography>
          
          <Grid container spacing={3} alignItems="center">
            <Grid item xs={12} md={3}>
              <TextField
                fullWidth
                label="Value"
                type="number"
                value={value}
                onChange={(e) => setValue(e.target.value)}
                variant="outlined"
                inputProps={{ step: 'any' }}
                sx={{
                  '& .MuiOutlinedInput-root': {
                    '& fieldset': { borderColor: 'primary.light' },
                    '&:hover fieldset': { borderColor: 'primary.main' },
                  }
                }}
              />
            </Grid>
            
            <Grid item xs={12} md={3}>
              <FormControl fullWidth>
                <InputLabel>From Unit</InputLabel>
                <Select
                  value={fromUnit}
                  onChange={(e) => setFromUnit(e.target.value)}
                  label="From Unit"
                >
                  {getUnitsForCategory(selectedCategory).map((unit) => {
                    const symbol = formatUnitDisplay(unit);
                    const name = formatUnitName(unit);
                    const label = symbol.toLowerCase() === name.toLowerCase() ? name : `${name} (${symbol})`;
                    return (
                      <MenuItem key={unit} value={unit}>
                        {label}
                      </MenuItem>
                    );
                  })}
                </Select>
              </FormControl>
            </Grid>
            
            <Grid item xs={12} md={1}>
              <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
                <Tooltip title="Swap units">
                  <IconButton 
                    onClick={handleSwapUnits}
                    sx={{
                      bgcolor: 'primary.light',
                      color: 'white',
                      '&:hover': { bgcolor: 'primary.main', transform: 'rotate(180deg)' },
                      transition: 'all 0.3s ease'
                    }}
                  >
                    <SwapHoriz />
                  </IconButton>
                </Tooltip>
              </Box>
            </Grid>
            
            <Grid item xs={12} md={3}>
              <FormControl fullWidth>
                <InputLabel>To Unit</InputLabel>
                <Select
                  value={toUnit}
                  onChange={(e) => setToUnit(e.target.value)}
                  label="To Unit"
                >
                  {getUnitsForCategory(selectedCategory).map((unit) => {
                    const symbol = formatUnitDisplay(unit);
                    const name = formatUnitName(unit);
                    const label = symbol.toLowerCase() === name.toLowerCase() ? name : `${name} (${symbol})`;
                    return (
                      <MenuItem key={unit} value={unit}>
                        {label}
                      </MenuItem>
                    );
                  })}
                </Select>
              </FormControl>
            </Grid>
            
            <Grid item xs={12} md={2}>
              <Stack direction="row" spacing={1}>
                <Button
                  variant="contained"
                  onClick={handleConvert}
                  disabled={isLoading}
                  fullWidth
                  sx={{
                    height: 56,
                    background: 'linear-gradient(45deg, #2196F3 30%, #21CBF3 90%)',
                    '&:hover': {
                      background: 'linear-gradient(45deg, #1976D2 30%, #1BA8D3 90%)',
                    }
                  }}
                >
                  {isLoading ? <CircularProgress size={24} color="inherit" /> : 'Convert'}
                </Button>
                
                {recognition && (
                  <Tooltip title="Voice Recognition">
                    <IconButton
                      onClick={handleVoiceRecognition}
                      sx={{
                        width: 56,
                        height: 56,
                        borderRadius: '50%',
                        bgcolor: isListening ? 'error.main' : 'primary.main',
                        color: 'white',
                        '&:hover': { 
                          bgcolor: isListening ? 'error.dark' : 'primary.dark',
                          transform: 'scale(1.1)'
                        },
                        transition: 'all 0.3s ease'
                      }}
                    >
                      {isListening ? <MicOff /> : <Mic />}
                    </IconButton>
                  </Tooltip>
                )}
              </Stack>
            </Grid>
          </Grid>
        </Paper>
      </Fade>

      {/* Error Display */}
      {error && (
        <Fade in={true}>
          <Alert severity="error" sx={{ mb: 3, fontSize: '1rem' }}>
            {error}
          </Alert>
        </Fade>
      )}

      {/* Result Display */}
      {result && (
        <Fade in={true}>
          <Card elevation={6} sx={{ 
            mb: 3,
            background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
            color: 'white'
          }}>
            <CardContent sx={{ textAlign: 'center', p: 4 }}>
              <Typography variant="h6" gutterBottom sx={{ opacity: 0.9 }}>
                Conversion Result
              </Typography>
              <Typography variant="h3" sx={{ fontWeight: 700, mb: 2 }}>
                {typeof result.convertedValue === 'number' 
                  ? result.convertedValue.toLocaleString(undefined, { maximumFractionDigits: 6 })
                  : result.convertedValue
                }
              </Typography>
              <Chip 
                label={formatUnitDisplay(result.toUnit)}
                sx={{ 
                  bgcolor: 'rgba(255,255,255,0.2)',
                  color: 'white',
                  fontSize: '1rem',
                  fontWeight: 600
                }}
              />
              <Typography variant="body2" sx={{ mt: 2, opacity: 0.8 }}>
                {value} {formatUnitDisplay(fromUnit)} = {typeof result.convertedValue === 'number' 
                  ? result.convertedValue.toLocaleString(undefined, { maximumFractionDigits: 6 })
                  : result.convertedValue
                } {formatUnitDisplay(toUnit)}
              </Typography>
            </CardContent>
          </Card>
        </Fade>
      )}

      {/* Popular Conversions */}
      {selectedCategory && popularConversions[selectedCategory] && (
        <Fade in={true}>
          <Box sx={{ mb: 3 }}>
            <Typography variant="h6" gutterBottom sx={{ fontWeight: 600, color: 'primary.main' }}>
              Popular {categoryDisplayNames[selectedCategory]} Conversions
            </Typography>
            <Grid container spacing={2}>
              {popularConversions[selectedCategory].map((conversion, index) => (
                <Grid item xs={12} sm={6} md={3} key={index}>
                  <Card 
                    sx={{ 
                      cursor: 'pointer',
                      transition: 'all 0.3s ease',
                      '&:hover': { 
                        transform: 'translateY(-4px)',
                        boxShadow: 8,
                        bgcolor: 'primary.light',
                        color: 'white'
                      }
                    }}
                    onClick={() => handleSuggestionClick(conversion)}
                  >
                    <CardContent sx={{ textAlign: 'center', p: 2 }}>
                      {(() => {
                        const fromSym = formatUnitDisplay(conversion.from);
                        const toSym = formatUnitDisplay(conversion.to);
                        const fromName = formatUnitName(conversion.from);
                        const toName = formatUnitName(conversion.to);
                        const fromLabel = fromSym.toLowerCase() === fromName.toLowerCase() ? fromName : `${fromName} (${fromSym})`;
                        const toLabel = toSym.toLowerCase() === toName.toLowerCase() ? toName : `${toName} (${toSym})`;
                        return (
                          <Typography variant="body2" sx={{ fontWeight: 500 }}>
                            {fromLabel} 	→	 {toLabel}
                          </Typography>
                        );
                      })()}
                    </CardContent>
                  </Card>
                </Grid>
              ))}
            </Grid>
          </Box>
        </Fade>
      )}

      {/* Quick Reference */}
      {selectedCategory && (
        <Fade in={true}>
          <Paper elevation={2} sx={{ p: 3, mt: 3 }}>
            <Typography variant="h6" gutterBottom sx={{ color: 'primary.main' }}>
              Available Units - {categoryDisplayNames[selectedCategory]}
            </Typography>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
              {getUnitsForCategory(selectedCategory).map((unit) => {
                const symbol = formatUnitDisplay(unit);
                const name = formatUnitName(unit);
                const label = symbol.toLowerCase() === name.toLowerCase() ? name : `${name} (${symbol})`;
                return (
                  <Chip
                    key={unit}
                    label={label}
                    variant="outlined"
                    size="small"
                    onClick={() => {
                      if (!fromUnit) {
                        setFromUnit(unit);
                      } else if (!toUnit) {
                        setToUnit(unit);
                      }
                    }}
                    sx={{
                      cursor: 'pointer',
                      color: 'text.primary',
                      borderColor: 'text.secondary',
                      '&:hover': {
                        bgcolor: 'primary.light',
                        color: 'white',
                        borderColor: 'primary.main'
                      }
                    }}
                  />
                );
              })}
            </Box>
          </Paper>
        </Fade>
      )}

      {/* Instructions */}
      {!selectedCategory && (
        <Paper elevation={1} sx={{ p: 3, bgcolor: 'info.light', color: 'info.contrastText' }}>
          <Typography variant="h6" gutterBottom>
            How to Use
          </Typography>
          <Typography variant="body2">
            1. Select a category above to start converting<br/>
            2. Enter the value you want to convert<br/>
            3. Choose source and target units<br/>
            4. Click Convert or use voice recognition
          </Typography>
        </Paper>
      )}
    </Box>
  );
};

export default UnitConversionComponent; 