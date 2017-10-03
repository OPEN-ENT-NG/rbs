-- MISE A JOUR des dates sans parent_booking_id de début et fin +2h (réservations débutant en heure d'été) ou +1h (réservations débutant en heure d'hiver)
UPDATE rbs.booking SET start_date = start_date + (start_date AT TIME ZONE 'UTC'-start_date) , end_date = end_date + (start_date AT TIME ZONE 'UTC'-start_date) WHERE parent_booking_id IS NULL;
-- MISE A JOUR des dates avec parent_booking_id à partir des dates des réservations du parent_booking_id
UPDATE rbs.booking AS fils
SET
  start_date = (SELECT fils.start_date + (date_part('hour',pere.start_date) - date_part('hour',fils.start_date) || ' hours')::interval FROM rbs.booking AS pere WHERE pere.id = fils.parent_booking_id)
  , end_date = (SELECT fils.end_date + (date_part('hour',pere.end_date) - date_part('hour',fils.end_date) || ' hours')::interval FROM rbs.booking AS pere WHERE pere.id = fils.parent_booking_id)
WHERE fils.parent_booking_id IS NOT NULL;