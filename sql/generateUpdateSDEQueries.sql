select concat(
          'TRUNCATE ', lower(table_name), ' CASCADE;'
          )from information_schema.tables where table_schema = 'evesde';

select concat(
          'TRUNCATE evesde."', table_name, '" CASCADE;'
          )from information_schema.tables where table_schema = 'evesde';

select concat(
          ' INSERT INTO ', lower(table_name),
          ' SELECT * FROM evesde."', table_name, '";'
           )from information_schema.tables where table_schema = 'evesde';
