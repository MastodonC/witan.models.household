# Introduction to witan.models.household


`witan.models.household` is a Clojure library that converts population projections into household projections and dwellings (housing demand) projections.

To learn more about the methodology, see the [Model](#Model) section. To learn more about the data inputs currently being used, jump to the [Data inputs](#data-inputs) section.

## Glossary
Let's define a few terms and acronyms we use throughout the library:

* LA: local authority.

* Household population: population living in private households
 
* Stage 1 household population: This refers to Department for Community and Local Government's (DCLG) household population breakdown by age group, sex and relationship status.  

* Relationship status: single, couple, or previously married - categories used by the DCLG in their Stage 1 household population breakdown.

* Institutional population: population not living in private households, such as residents of student halls, care homes, military bases, etc.

* Resident population: total population in the context of the household model. The sum of household and institutional populations.

* Household representative rates: the proportion of the population in each category (age group, sex and relationship) of the household population who are household representatives (heads of households).


## Content of the library
* [DCLG-based Household and Dwellings Projections Model](#household-model)



## DCLG-based Household and Dwellings Projections Model

This flowchart shows the steps of the household model. Different background colors correspond to the major steps of the model.

![HHM](https://github.com/MastodonC/witan.models.household/blob/master/img/HHM_flowchart.png)
(Please download the image for a more detailed overview of the model)

###Model steps

#### 1. Apportion the population across the relationship types

Takes a population projection broken down by sex, age, year, and LA. Apportions the population across the relationship types to get a resident population that is broken down by stage 1 relationships (as well as sex, age, year and LA). The proportions of  relationship types for each sex-age-year-LA combination are calculated from the DCLG resident population projection (DCLG household population + DCLG institutional population).

#### 2. Calculate the institutional population

For age groups under 75, the institutional population is set to value of the DCLG institutional population. However for over 75 age groups, the institutional population (in care homes) is linked to the population projections entering the model and therefore are calculated from the resident population.

#### 3. Calculate the household population

Calculates the household population by substracting the institutional population from the resident population.

#### 4. Calculate households and sum to total households

Calculates the number of households by multiplying the household population and the household representative rates. Households are then summed to get a total number of households by area and year.

#### 5. Convert households to dwellings

Calculates the number of dwellings from the total number of households using the vacancy rate (calculated from the numbers of dwellings and vacant dwellings in the most recent year of historical data) and second home rate (a user-defined parameter).

###Parameters

#### User-defined parameters for the model

Some parameters in the model can be specified by the user. The parameters, with defaults in parentheses, are:

* Second home rate (0.0): proportion of dwellings that are second homes. 

#### User-defined parameters for the geography

The geographical area for the projection must be specified. This is done with the 9-digit GSS code (Government Statistical Service code), or name, of one of the following:

  * an English unitary authority (starts with "E06")
  * an English non-metropolitan district (starts with "E07")
  * an English metropolitan borough (starts with "E08")
  * a London borough (starts with "E09")

### Data inputs

The housing- and household-related inputs required for the model comes from the UK's Department for Communities and Local Government (DCLG). The datasets used are:

* Household population projection (stage 1, 2014-based)
* Household representative rate projection (stage 1, 2014-based)
* Institutional population projection (stage 1, 2014-based)
* Dwelling stock for local authorities in England
* Vacant dwellings for local authorities in England

The population projection currently comes from the UK's Office of National Statistics, but in practice can come from any source that produces a population projection broken down by age, sex, year and GSS code. The dataset used is the:

* Subnational population projection for local authorities in England

Further information regarding these data inputs can be found [here](https://github.com/MastodonC/witan.models.household/blob/master/doc/Data_sources.csv)
